package com.example.maze;

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

import java.util.List;

public class MazeCanvas extends Canvas {

    private static final Color BACKGROUND = Color.web("#F2F3F7");
    private static final Color WALL = Color.web("#2B2F36");
    private static final Color OPEN = Color.web("#FFFFFF");
    private static final Color PATH = Color.web("#2FD3A5");
    private static final Color START = Color.web("#34C77B");
    private static final Color END = Color.web("#F03E4D");
    private static final Color PLAYER = Color.web("#3B82F6");
    private static final Color OPPONENT = Color.web("#F59E0B");

    private Maze maze;
    private List<int[]> path = List.of();
    private int playerX;
    private int playerY;
    private boolean hasPlayer;
    private int opponentX;
    private int opponentY;
    private boolean hasOpponent;

    public MazeCanvas(double width, double height) {
        super(width, height);
    }

    public void setMaze(Maze maze) {
        this.maze = maze;
        this.path = List.of();
        this.hasOpponent = false;
        int[] start = maze.start();
        hasPlayer = start != null;
        if (start != null) {
            playerX = start[0];
            playerY = start[1];
        }
        draw();
    }

    public Maze getMaze() {
        return maze;
    }

    public int[] playerPos() {
        return new int[]{playerX, playerY};
    }

    public void setOpponent(int x, int y) {
        opponentX = x;
        opponentY = y;
        hasOpponent = true;
        draw();
    }

    public void clearOpponent() {
        hasOpponent = false;
        draw();
    }

    public void setPath(List<int[]> path) {
        this.path = path;
        draw();
    }

    public boolean movePlayer(int dx, int dy) {
        if (!hasPlayer) {
            return false;
        }
        int nx = playerX + dx;
        int ny = playerY + dy;
        if (nx < 0 || ny < 0 || nx >= maze.width() || ny >= maze.height() || maze.isWall(nx, ny)) {
            return false;
        }
        playerX = nx;
        playerY = ny;
        draw();
        return true;
    }

    public boolean isAtEnd() {
        if (!hasPlayer) {
            return false;
        }
        int[] end = maze.end();
        return end != null && playerX == end[0] && playerY == end[1];
    }

    private double cell() {
        return Math.min(getWidth() / maze.width(), getHeight() / maze.height());
    }

    private double offsetX() {
        return (getWidth() - cell() * maze.width()) / 2;
    }

    private double offsetY() {
        return (getHeight() - cell() * maze.height()) / 2;
    }

    public void draw() {
        GraphicsContext g = getGraphicsContext2D();
        g.setFill(BACKGROUND);
        g.fillRect(0, 0, getWidth(), getHeight());
        if (maze == null) {
            return;
        }
        double cell = cell();
        double ox = offsetX();
        double oy = offsetY();
        for (int y = 0; y < maze.height(); y++) {
            for (int x = 0; x < maze.width(); x++) {
                g.setFill(maze.isWall(x, y) ? WALL : OPEN);
                g.fillRect(ox + x * cell, oy + y * cell, cell + 0.5, cell + 0.5);
            }
        }
        g.setFill(PATH);
        for (int[] point : path) {
            g.fillRect(ox + point[0] * cell + cell * 0.25, oy + point[1] * cell + cell * 0.25,
                    cell * 0.5, cell * 0.5);
        }
        int[] start = maze.start();
        int[] end = maze.end();
        if (start != null) {
            g.setFill(START);
            g.fillOval(ox + start[0] * cell + cell * 0.15, oy + start[1] * cell + cell * 0.15,
                    cell * 0.7, cell * 0.7);
        }
        if (end != null) {
            g.setFill(END);
            g.fillOval(ox + end[0] * cell + cell * 0.15, oy + end[1] * cell + cell * 0.15,
                    cell * 0.7, cell * 0.7);
        }
        if (hasOpponent) {
            g.setFill(OPPONENT);
            g.fillOval(ox + opponentX * cell + cell * 0.1, oy + opponentY * cell + cell * 0.1,
                    cell * 0.8, cell * 0.8);
        }
        if (hasPlayer) {
            g.setFill(PLAYER);
            g.fillOval(ox + playerX * cell + cell * 0.1, oy + playerY * cell + cell * 0.1,
                    cell * 0.8, cell * 0.8);
        }
    }
}
