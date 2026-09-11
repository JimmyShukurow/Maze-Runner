package com.example.maze;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Random;

public final class Maze {

    private static final double BRAID_RATIO = 0.35;

    private final int width;
    private final int height;
    private final boolean[][] wall;

    public Maze(int width, int height) {
        this.width = width;
        this.height = height;
        this.wall = new boolean[height][width];
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public boolean isWall(int x, int y) {
        return wall[y][x];
    }

    private void setWall(int x, int y, boolean value) {
        wall[y][x] = value;
    }

    public void fill(boolean value) {
        for (boolean[] row : wall) {
            Arrays.fill(row, value);
        }
    }

    public static Maze generate(int requestedWidth, int requestedHeight, Random random) {
        int width = requestedWidth | 1;
        int height = requestedHeight | 1;
        Maze maze = new Maze(width, height);
        maze.fill(true);
        maze.setWall(1, 1, false);

        int[][] steps = {{2, 0}, {-2, 0}, {0, 2}, {0, -2}};
        Deque<int[]> stack = new ArrayDeque<>();
        stack.push(new int[]{1, 1});
        while (!stack.isEmpty()) {
            int[] current = stack.peek();
            List<int[]> options = new ArrayList<>();
            for (int[] step : steps) {
                int nx = current[0] + step[0];
                int ny = current[1] + step[1];
                if (nx > 0 && ny > 0 && nx < width - 1 && ny < height - 1 && maze.isWall(nx, ny)) {
                    options.add(new int[]{nx, ny, current[0] + step[0] / 2, current[1] + step[1] / 2});
                }
            }
            if (options.isEmpty()) {
                stack.pop();
                continue;
            }
            int[] next = options.get(random.nextInt(options.size()));
            maze.setWall(next[2], next[3], false);
            maze.setWall(next[0], next[1], false);
            stack.push(new int[]{next[0], next[1]});
        }
        maze.braid(random);
        return maze;
    }

    private void braid(Random random) {
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int y = 1; y < height - 1; y++) {
            for (int x = 1; x < width - 1; x++) {
                if (wall[y][x]) {
                    continue;
                }
                int openNeighbours = 0;
                for (int[] dir : dirs) {
                    if (!wall[y + dir[1]][x + dir[0]]) {
                        openNeighbours++;
                    }
                }
                if (openNeighbours != 1 || random.nextDouble() >= BRAID_RATIO) {
                    continue;
                }
                List<int[]> candidates = new ArrayList<>();
                for (int[] dir : dirs) {
                    int wallX = x + dir[0];
                    int wallY = y + dir[1];
                    int beyondX = x + 2 * dir[0];
                    int beyondY = y + 2 * dir[1];
                    if (beyondX > 0 && beyondY > 0 && beyondX < width - 1 && beyondY < height - 1
                            && wall[wallY][wallX] && !wall[beyondY][beyondX]) {
                        candidates.add(new int[]{wallX, wallY});
                    }
                }
                if (!candidates.isEmpty()) {
                    int[] chosen = candidates.get(random.nextInt(candidates.size()));
                    wall[chosen[1]][chosen[0]] = false;
                }
            }
        }
    }

    public int[] start() {
        return nearestOpen(0, 0);
    }

    public int[] end() {
        return nearestOpen(width - 1, height - 1);
    }

    private int[] nearestOpen(int fromX, int fromY) {
        int[] best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (!wall[y][x]) {
                    int distance = Math.abs(x - fromX) + Math.abs(y - fromY);
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        best = new int[]{x, y};
                    }
                }
            }
        }
        return best;
    }

    public List<int[]> solve() {
        int[] start = start();
        int[] end = end();
        if (start == null || end == null) {
            return List.of();
        }
        return solve(start[0], start[1], end[0], end[1]);
    }

    public List<int[]> solve(int startX, int startY, int targetX, int targetY) {
        if (isWall(startX, startY) || isWall(targetX, targetY)) {
            return List.of();
        }
        int[] parent = new int[width * height];
        Arrays.fill(parent, -1);
        boolean[] seen = new boolean[width * height];
        int[][] steps = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        Deque<int[]> queue = new ArrayDeque<>();
        queue.add(new int[]{startX, startY});
        seen[startY * width + startX] = true;
        while (!queue.isEmpty()) {
            int[] current = queue.poll();
            if (current[0] == targetX && current[1] == targetY) {
                List<int[]> path = new ArrayList<>();
                int index = targetY * width + targetX;
                while (index != -1) {
                    path.add(new int[]{index % width, index / width});
                    index = parent[index];
                }
                Collections.reverse(path);
                return path;
            }
            for (int[] step : steps) {
                int nx = current[0] + step[0];
                int ny = current[1] + step[1];
                int nextIndex = ny * width + nx;
                if (nx >= 0 && ny >= 0 && nx < width && ny < height
                        && !seen[nextIndex] && !wall[ny][nx]) {
                    seen[nextIndex] = true;
                    parent[nextIndex] = current[1] * width + current[0];
                    queue.add(new int[]{nx, ny});
                }
            }
        }
        return List.of();
    }

    public void save(Path file) throws IOException {
        StringBuilder text = new StringBuilder();
        text.append(width).append(' ').append(height).append('\n');
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                text.append(wall[y][x] ? '#' : '.');
            }
            text.append('\n');
        }
        Files.writeString(file, text.toString());
    }

    public static Maze fromRows(int width, int height, String rows) {
        Maze maze = new Maze(width, height);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                maze.wall[y][x] = rows.charAt(y * width + x) == '#';
            }
        }
        return maze;
    }

    public static Maze load(Path file) throws IOException {
        List<String> lines = Files.readAllLines(file);
        String[] dimensions = lines.get(0).trim().split("\\s+");
        int width = Integer.parseInt(dimensions[0]);
        int height = Integer.parseInt(dimensions[1]);
        Maze maze = new Maze(width, height);
        for (int y = 0; y < height; y++) {
            String line = lines.get(y + 1);
            for (int x = 0; x < width; x++) {
                maze.wall[y][x] = line.charAt(x) == '#';
            }
        }
        return maze;
    }
}
