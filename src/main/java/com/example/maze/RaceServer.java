package com.example.maze;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class RaceServer implements Closeable {

    private final int port;
    private final int mazeSize;
    private final Object lock = new Object();
    private final List<Conn> players = new ArrayList<>();
    private ServerSocket serverSocket;
    private boolean started;
    private boolean finished;

    public RaceServer(int port, int mazeSize) {
        this.port = port;
        this.mazeSize = mazeSize;
    }

    public void start() throws IOException {
        serverSocket = new ServerSocket(port);
        Thread acceptor = new Thread(this::acceptLoop, "race-accept");
        acceptor.setDaemon(true);
        acceptor.start();
    }

    private void acceptLoop() {
        while (!serverSocket.isClosed()) {
            try {
                Socket socket = serverSocket.accept();
                Conn conn = new Conn(socket);
                Thread reader = new Thread(conn::readLoop, "race-player");
                reader.setDaemon(true);
                reader.start();
            } catch (IOException e) {
                return;
            }
        }
    }

    @Override
    public void close() {
        try {
            serverSocket.close();
        } catch (IOException ignored) {
            // closing is best effort
        }
        synchronized (lock) {
            for (Conn conn : players) {
                conn.close();
            }
        }
    }

    private void begin() {
        Maze maze = Maze.generate(mazeSize, mazeSize, new Random());
        StringBuilder rows = new StringBuilder();
        for (int y = 0; y < maze.height(); y++) {
            for (int x = 0; x < maze.width(); x++) {
                rows.append(maze.isWall(x, y) ? '#' : '.');
            }
        }
        for (int i = 0; i < players.size(); i++) {
            Conn conn = players.get(i);
            Conn opponent = players.get(1 - i);
            conn.send("OPP\t" + opponent.name);
            conn.send("MAZE\t" + maze.width() + "\t" + maze.height() + "\t" + rows);
            conn.send("BEGIN");
        }
    }

    private void broadcast(String message) {
        for (Conn conn : players) {
            conn.send(message);
        }
    }

    private final class Conn {
        private final Socket socket;
        private final PrintWriter out;
        private final BufferedReader in;
        private String name = "player";

        private Conn(Socket socket) throws IOException {
            this.socket = socket;
            this.out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
            this.in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        }

        private void readLoop() {
            try {
                String hello = in.readLine();
                if (hello == null || !hello.startsWith("HELLO\t")) {
                    return;
                }
                name = sanitize(hello.substring("HELLO\t".length()));
                synchronized (lock) {
                    if (started || players.size() == 2) {
                        out.println("FULL");
                        return;
                    }
                    players.add(this);
                    if (players.size() == 2) {
                        started = true;
                        begin();
                    }
                }
                String line;
                while ((line = in.readLine()) != null) {
                    handle(line);
                }
            } catch (IOException e) {
                // peer went away
            } finally {
                disconnected();
            }
        }

        private void handle(String line) {
            String[] parts = line.split("\t");
            synchronized (lock) {
                if (!started || finished) {
                    return;
                }
                if (parts[0].equals("POS") && parts.length == 3) {
                    for (Conn other : players) {
                        if (other != this) {
                            other.send("OPPPOS\t" + parts[1] + "\t" + parts[2]);
                        }
                    }
                } else if (parts[0].equals("DONE") && parts.length == 2) {
                    finished = true;
                    broadcast("WIN\t" + name + "\t" + parts[1]);
                }
            }
        }

        private void disconnected() {
            synchronized (lock) {
                boolean wasPlaying = players.remove(this);
                if (wasPlaying && started && !finished) {
                    finished = true;
                    broadcast("OPPLEFT");
                }
            }
            close();
        }

        private void send(String message) {
            out.println(message);
        }

        private void close() {
            try {
                socket.close();
            } catch (IOException ignored) {
                // closing is best effort
            }
        }
    }

    private static String sanitize(String raw) {
        String name = raw.trim().replaceAll("\\s+", " ");
        if (name.isEmpty()) {
            return "player";
        }
        return name.length() > 24 ? name.substring(0, 24) : name;
    }
}
