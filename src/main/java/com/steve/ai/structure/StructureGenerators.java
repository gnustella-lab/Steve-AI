package com.steve.ai.structure;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Utility class for procedural structure generation.
 * Contains algorithms for generating various building types.
 */
public class StructureGenerators {

    public static List<BlockPlacement> generate(String structureType, BlockPos start, int width, int height, int depth, List<Block> materials) {
        return switch (structureType.toLowerCase()) {
            case "house", "home" -> buildAdvancedHouse(start, width, height, depth, materials);
            case "castle", "catle", "fort" -> buildCastle(start, width, height, depth, materials);
            case "tower" -> buildAdvancedTower(start, width, height, materials);
            case "wall" -> buildWall(start, width, height, materials);
            case "platform" -> buildPlatform(start, width, depth, materials);
            case "barn", "shed" -> buildBarn(start, width, height, depth, materials);
            case "modern", "modern_house" -> buildModernHouse(start, width, height, depth, materials);
            case "box", "cube" -> buildBox(start, width, height, depth, materials);
            default -> buildAdvancedHouse(start, Math.max(5, width), Math.max(4, height), Math.max(5, depth), materials);
        };
    }

    private static Block getMaterial(List<Block> materials, int index) {
        if (materials.isEmpty()) return Blocks.OAK_PLANKS;
        return materials.get(index % materials.size());
    }

    private static List<BlockPlacement> buildAdvancedHouse(BlockPos start, int width, int height, int depth, List<Block> materials) {
        width = Math.max(5, width);
        height = Math.max(4, height);
        depth = Math.max(5, depth);

        Map<BlockPos, BlockState> cells = new LinkedHashMap<>();
        Block floorMaterial = getMaterial(materials, 0);
        Block wallMaterial = materials.size() > 1 ? getMaterial(materials, 1) : floorMaterial;
        Block roofMaterial = materials.size() > 2 ? getMaterial(materials, 2) : wallMaterial;
        Block windowMaterial = resolveWindowMaterial(materials);
        int doorX = width / 2;

        for (int x = 0; x < width; x++) {
            for (int z = 0; z < depth; z++) {
                cells.put(start.offset(x, 0, z), floorMaterial.defaultBlockState());
            }
        }

        for (int y = 1; y < height; y++) {
            for (int x = 0; x < width; x++) {
                putFrontOrBackWall(cells, start.offset(x, y, 0), x, y, width, doorX, true, wallMaterial, windowMaterial);
                putFrontOrBackWall(cells, start.offset(x, y, depth - 1), x, y, width, doorX, false, wallMaterial, windowMaterial);
            }
            for (int z = 1; z < depth - 1; z++) {
                putSideWall(cells, start.offset(0, y, z), z, y, depth, wallMaterial, windowMaterial);
                putSideWall(cells, start.offset(width - 1, y, z), z, y, depth, wallMaterial, windowMaterial);
            }
        }

        for (int x = 0; x < width; x++) {
            for (int z = 0; z < depth; z++) {
                cells.put(start.offset(x, height, z), roofMaterial.defaultBlockState());
            }
        }

        List<BlockPlacement> blocks = new ArrayList<>(cells.size());
        for (Map.Entry<BlockPos, BlockState> entry : cells.entrySet()) {
            blocks.add(new BlockPlacement(entry.getKey(), entry.getValue()));
        }
        return blocks;
    }

    private static Block resolveWindowMaterial(List<Block> materials) {
        for (Block block : materials) {
            if (block == Blocks.GLASS || block == Blocks.GLASS_PANE) {
                return block;
            }
        }
        return Blocks.AIR;
    }

    private static boolean isWindowColumn(int along, int span, int doorAlong) {
        if (along <= 0 || along >= span - 1 || along == doorAlong) {
            return false;
        }
        return along == 2 || along == span - 3 || along == span / 2;
    }

    private static void putFrontOrBackWall(Map<BlockPos, BlockState> cells, BlockPos pos, int x, int y,
            int width, int doorX, boolean front, Block wallMaterial, Block windowMaterial) {
        if (front && x == doorX && y <= 2) {
            cells.put(pos, Blocks.AIR.defaultBlockState());
            return;
        }
        if (y == 2 && isWindowColumn(x, width, front ? doorX : -1)) {
            cells.put(pos, windowMaterial.defaultBlockState());
            return;
        }
        cells.put(pos, wallMaterial.defaultBlockState());
    }

    private static void putSideWall(Map<BlockPos, BlockState> cells, BlockPos pos, int z, int y, int depth,
            Block wallMaterial, Block windowMaterial) {
        if (y == 2 && z % 3 == 1 && z > 0 && z < depth - 1) {
            cells.put(pos, windowMaterial.defaultBlockState());
            return;
        }
        cells.put(pos, wallMaterial.defaultBlockState());
    }

    private static List<BlockPlacement> buildCastle(BlockPos start, int width, int height, int depth, List<Block> materials) {
        List<BlockPlacement> blocks = new ArrayList<>();
        Block stoneMaterial = Blocks.STONE_BRICKS;
        Block wallMaterial = Blocks.COBBLESTONE;
        Block windowMaterial = Blocks.GLASS_PANE;

        // Main structure
        for (int y = 0; y <= height; y++) {
            for (int x = 0; x < width; x++) {
                for (int z = 0; z < depth; z++) {
                    boolean isEdge = (x == 0 || x == width - 1 || z == 0 || z == depth - 1);
                    boolean isCorner = (x <= 2 || x >= width - 3) && (z <= 2 || z >= depth - 3);

                    if (y == 0) {
                        blocks.add(new BlockPlacement(start.offset(x, y, z), stoneMaterial));
                    } else if (isEdge && !isCorner) {
                        if (x == width / 2 && z == 0 && y <= 3) {
                            if (y >= 1 && y <= 3 && x >= width / 2 - 1 && x <= width / 2 + 1) {
                                blocks.add(new BlockPlacement(start.offset(x, y, 0), Blocks.AIR));
                            }
                        } else if (y % 4 == 2 && !isCorner) {
                            blocks.add(new BlockPlacement(start.offset(x, y, z), windowMaterial));
                        } else {
                            blocks.add(new BlockPlacement(start.offset(x, y, z), wallMaterial));
                        }
                    }
                }
            }
        }

        // Corner towers
        int towerHeight = height + 6;
        int towerSize = 3;
        int[][] corners = {{0, 0}, {width - towerSize, 0}, {0, depth - towerSize}, {width - towerSize, depth - towerSize}};

        for (int[] corner : corners) {
            for (int y = 0; y <= towerHeight; y++) {
                for (int dx = 0; dx < towerSize; dx++) {
                    for (int dz = 0; dz < towerSize; dz++) {
                        boolean isTowerEdge = (dx == 0 || dx == towerSize - 1 || dz == 0 || dz == towerSize - 1);

                        if (y == 0 || isTowerEdge) {
                            blocks.add(new BlockPlacement(start.offset(corner[0] + dx, y, corner[1] + dz), stoneMaterial));
                        }

                        if (y % 5 == 3 && isTowerEdge && (dx == towerSize / 2 || dz == towerSize / 2)) {
                            blocks.add(new BlockPlacement(start.offset(corner[0] + dx, y, corner[1] + dz), windowMaterial));
                        }
                    }
                }
            }

            // Tower crenellations
            for (int dx = 0; dx < towerSize; dx++) {
                for (int dz = 0; dz < towerSize; dz++) {
                    if (dx % 2 == 0 || dz % 2 == 0) {
                        blocks.add(new BlockPlacement(start.offset(corner[0] + dx, towerHeight + 1, corner[1] + dz), stoneMaterial));
                    }
                }
            }
        }

        // Wall crenellations
        for (int x = 0; x < width; x += 2) {
            blocks.add(new BlockPlacement(start.offset(x, height + 1, 0), stoneMaterial));
            blocks.add(new BlockPlacement(start.offset(x, height + 2, 0), stoneMaterial));
            blocks.add(new BlockPlacement(start.offset(x, height + 1, depth - 1), stoneMaterial));
            blocks.add(new BlockPlacement(start.offset(x, height + 2, depth - 1), stoneMaterial));
        }

        for (int z = 0; z < depth; z += 2) {
            blocks.add(new BlockPlacement(start.offset(0, height + 1, z), stoneMaterial));
            blocks.add(new BlockPlacement(start.offset(0, height + 2, z), stoneMaterial));
            blocks.add(new BlockPlacement(start.offset(width - 1, height + 1, z), stoneMaterial));
            blocks.add(new BlockPlacement(start.offset(width - 1, height + 2, z), stoneMaterial));
        }

        return blocks;
    }

    private static List<BlockPlacement> buildAdvancedTower(BlockPos start, int width, int height, List<Block> materials) {
        List<BlockPlacement> blocks = new ArrayList<>();
        Block wallMaterial = Blocks.STONE_BRICKS;
        Block accentMaterial = Blocks.CHISELED_STONE_BRICKS;
        Block windowMaterial = Blocks.GLASS_PANE;
        Block roofMaterial = Blocks.DARK_OAK_STAIRS;

        // Main tower body
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                for (int z = 0; z < width; z++) {
                    boolean isEdge = (x == 0 || x == width - 1 || z == 0 || z == width - 1);
                    boolean isCorner = (x == 0 || x == width - 1) && (z == 0 || z == width - 1);

                    if (y == 0) {
                        blocks.add(new BlockPlacement(start.offset(x, y, z), wallMaterial));
                    } else if (isEdge) {
                        if (y % 3 == 2 && !isCorner && (x == width / 2 || z == width / 2)) {
                            blocks.add(new BlockPlacement(start.offset(x, y, z), windowMaterial));
                        } else if (isCorner) {
                            blocks.add(new BlockPlacement(start.offset(x, y, z), accentMaterial));
                        } else {
                            blocks.add(new BlockPlacement(start.offset(x, y, z), wallMaterial));
                        }
                    }
                }
            }
        }

        // Pyramid roof
        for (int i = 0; i < width / 2 + 1; i++) {
            for (int x = i; x < width - i; x++) {
                for (int z = i; z < width - i; z++) {
                    if (x == i || x == width - 1 - i || z == i || z == width - 1 - i) {
                        blocks.add(new BlockPlacement(start.offset(x, height + i, z), roofMaterial));
                    }
                }
            }
        }

        return blocks;
    }

    private static List<BlockPlacement> buildModernHouse(BlockPos start, int width, int height, int depth, List<Block> materials) {
        List<BlockPlacement> blocks = new ArrayList<>();
        Block wallMaterial = Blocks.QUARTZ_BLOCK;
        Block floorMaterial = Blocks.SMOOTH_STONE;
        Block glassMaterial = Blocks.GLASS;
        Block roofMaterial = Blocks.DARK_OAK_PLANKS;

        // Floor
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < depth; z++) {
                blocks.add(new BlockPlacement(start.offset(x, 0, z), floorMaterial));
            }
        }

        // Modern walls with lots of glass
        for (int y = 1; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (x % 2 == 0 || y > 1) {
                    blocks.add(new BlockPlacement(start.offset(x, y, 0), glassMaterial));
                } else {
                    blocks.add(new BlockPlacement(start.offset(x, y, 0), wallMaterial));
                }

                blocks.add(new BlockPlacement(start.offset(x, y, depth - 1), wallMaterial));
            }

            for (int z = 1; z < depth - 1; z++) {
                if (z % 3 == 1 && y == 2) {
                    blocks.add(new BlockPlacement(start.offset(0, y, z), glassMaterial));
                    blocks.add(new BlockPlacement(start.offset(width - 1, y, z), glassMaterial));
                } else {
                    blocks.add(new BlockPlacement(start.offset(0, y, z), wallMaterial));
                    blocks.add(new BlockPlacement(start.offset(width - 1, y, z), wallMaterial));
                }
            }
        }

        // Flat roof
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < depth; z++) {
                blocks.add(new BlockPlacement(start.offset(x, height, z), roofMaterial));
            }
        }

        return blocks;
    }

    private static List<BlockPlacement> buildBarn(BlockPos start, int width, int height, int depth, List<Block> materials) {
        List<BlockPlacement> blocks = new ArrayList<>();
        Block woodMaterial = Blocks.OAK_PLANKS;
        Block logMaterial = Blocks.OAK_LOG;
        Block roofMaterial = Blocks.SPRUCE_PLANKS;

        // Floor
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < depth; z++) {
                blocks.add(new BlockPlacement(start.offset(x, 0, z), woodMaterial));
            }
        }

        // Walls
        for (int y = 1; y < height; y++) {
            for (int x = 0; x < width; x++) {
                boolean isSupport = (x == 0 || x == width - 1 || x == width / 2);
                Block material = isSupport ? logMaterial : woodMaterial;

                if (x >= width / 3 && x <= 2 * width / 3 && y <= 2) {
                    continue; // Large door opening
                }

                blocks.add(new BlockPlacement(start.offset(x, y, 0), material));
                blocks.add(new BlockPlacement(start.offset(x, y, depth - 1), material));
            }

            for (int z = 1; z < depth - 1; z++) {
                blocks.add(new BlockPlacement(start.offset(0, y, z), logMaterial));
                blocks.add(new BlockPlacement(start.offset(width - 1, y, z), logMaterial));
            }
        }

        // Peaked roof
        int roofPeakHeight = height + width / 2;
        for (int x = 0; x < width; x++) {
            int distFromCenter = Math.abs(x - width / 2);
            int roofY = roofPeakHeight - distFromCenter;

            for (int z = 0; z < depth; z++) {
                blocks.add(new BlockPlacement(start.offset(x, roofY, z), roofMaterial));
            }
        }

        return blocks;
    }

    private static List<BlockPlacement> buildWall(BlockPos start, int width, int height, List<Block> materials) {
        List<BlockPlacement> blocks = new ArrayList<>();
        Block material = getMaterial(materials, 0);

        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                blocks.add(new BlockPlacement(start.offset(x, y, 0), material));
            }
        }

        return blocks;
    }

    private static List<BlockPlacement> buildPlatform(BlockPos start, int width, int depth, List<Block> materials) {
        List<BlockPlacement> blocks = new ArrayList<>();
        Block material = getMaterial(materials, 0);

        for (int x = 0; x < width; x++) {
            for (int z = 0; z < depth; z++) {
                blocks.add(new BlockPlacement(start.offset(x, 0, z), material));
            }
        }

        return blocks;
    }

    private static List<BlockPlacement> buildBox(BlockPos start, int width, int height, int depth, List<Block> materials) {
        List<BlockPlacement> blocks = new ArrayList<>();
        Block material = getMaterial(materials, 0);

        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                for (int z = 0; z < depth; z++) {
                    blocks.add(new BlockPlacement(start.offset(x, y, z), material));
                }
            }
        }

        return blocks;
    }
}
