package com.example.maze;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public final class RaceClient implements Closeable {

    public interface Listener {
        void onOpponent(String name);

        void onMaze(Maze maze);

        void onBegin();

        void onOpponentPos(int x, int y);

        void onWin(String name, int moves);

        void onOpponentLeft();

        void onError(String message);
    }

    private final String host;
    private final int port;
    private final String name;
    private final Listener listener;
    private Socket socket;
    private PrintWriter out;
    private volatile boolean closed;

    public RaceClient(String host, int port, String name, Listener listener) {
        this.host = host;
        this.port = port;
        this.name = name;
        this.listener = listener;
    }

    public void start() {
        Thread thread = new Thread(this::connect, "race-client");
        thread.setDaemon(true);
        thread.start();
    }

    private void connect() {
        boolean began = false;
        try {
            socket = new Socket(host, port);
            out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
            out.println("HELLO\t" + name);
            BufferedReader in = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            String line;
            while ((line = in.readLine()) != null) {
                if (line.equals("BEGIN")) {
                    began = true;
                }
                dispatch(line);
            }
            if (!began && !closed) {
                listener.onError("Connection closed before the race started.");
            }
        } catch (IOException e) {
            if (!closed) {
                listener.onError("Connection failed: " + e.getMessage());
            }
        }
    }

    private void dispatch(String line) {
        String[] parts = line.split("\t", -1);
        switch (parts[0]) {
            case "FULL" -> listener.onError("Server already has two players.");
            case "OPP" -> listener.onOpponent(parts[1]);
            case "MAZE" -> listener.onMaze(Maze.fromRows(
                    Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), parts[3]));
            case "BEGIN" -> listener.onBegin();
            case "OPPPOS" -> listener.onOpponentPos(Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
            case "WIN" -> listener.onWin(parts[1], Integer.parseInt(parts[2]));
            case "OPPLEFT" -> listener.onOpponentLeft();
            default -> {
            }
        }
    }

    public void sendPos(int x, int y) {
        if (out != null) {
            out.println("POS\t" + x + "\t" + y);
        }
    }

    public void sendDone(int moves) {
        if (out != null) {
            out.println("DONE\t" + moves);
        }
    }

    @Override
    public void close() {
        closed = true;
        try {
            if (socket != null) {
                socket.close();
            }
        } catch (IOException ignored) {
            // closing is best effort
        }
    }
}
