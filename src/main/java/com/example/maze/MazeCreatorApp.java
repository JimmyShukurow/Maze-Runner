package com.example.maze;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputControl;
import javafx.scene.image.WritableImage;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import javax.imageio.ImageIO;
import java.io.File;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;
import java.util.List;
import java.util.Random;

public class MazeCreatorApp extends Application {

    private final MazeCanvas canvas = new MazeCanvas(860, 600);
    private final Label status = new Label("Generate a maze, then reach the red end with the arrow keys.");
    private final ChoiceBox<Integer> sizeBox = new ChoiceBox<>();
    private final TextField nameField = new TextField(System.getProperty("user.name", "player"));
    private final TextField addressField = new TextField("127.0.0.1:7777");
    private final Button generateButton = new Button("Generate");
    private final Button solveButton = new Button("Solve");
    private final Button loadButton = new Button("Load");
    private final Button hostButton = new Button("Host race");
    private final Button joinButton = new Button("Join race");
    private int moves;
    private Stage stage;
    private RaceServer server;
    private RaceClient client;
    private String myName = "player";
    private boolean racing;
    private boolean raceFinished;

    @Override
    public void start(Stage stage) {
        sizeBox.getItems().addAll(21, 31, 41, 51, 61, 71);
        sizeBox.getSelectionModel().select(Integer.valueOf(41));

        generateButton.setOnAction(e -> {
            int size = sizeBox.getValue();
            Maze maze = Maze.generate(size, size, new Random());
            showMaze(maze, "Generated " + maze.width() + "x" + maze.height() + " maze.");
        });

        solveButton.setOnAction(e -> {
            List<int[]> path = canvas.getMaze().solve();
            canvas.setPath(path);
            status.setText(path.isEmpty()
                    ? "No path from start to end."
                    : "Solved in " + path.size() + " steps (green start, red end).");
        });

        Button clearButton = new Button("Clear solution");
        clearButton.setOnAction(e -> {
            canvas.setPath(List.of());
            status.setText("Solution cleared.");
        });

        Button saveButton = new Button("Save");
        saveButton.setOnAction(e -> {
            File file = chooser("Save maze", "*.maze").showSaveDialog(stage);
            if (file == null) {
                return;
            }
            try {
                canvas.getMaze().save(file.toPath());
                status.setText("Saved to " + file.getAbsolutePath());
            } catch (IOException ex) {
                status.setText("Save failed: " + ex.getMessage());
            }
        });

        loadButton.setOnAction(e -> {
            File file = chooser("Load maze", "*.maze").showOpenDialog(stage);
            if (file == null) {
                return;
            }
            try {
                Maze maze = Maze.load(file.toPath());
                showMaze(maze, "Loaded " + maze.width() + "x" + maze.height() + " maze from " + file.getName() + ".");
            } catch (IOException ex) {
                status.setText("Load failed: " + ex.getMessage());
            }
        });

        Button exportButton = new Button("Export PNG");
        exportButton.setOnAction(e -> {
            File file = chooser("Export PNG", "*.png").showSaveDialog(stage);
            if (file == null) {
                return;
            }
            try {
                WritableImage image = canvas.snapshot(null, null);
                ImageIO.write(SwingFXUtils.fromFXImage(image, null), "png", file);
                status.setText("Exported to " + file.getAbsolutePath());
            } catch (IOException ex) {
                status.setText("Export failed: " + ex.getMessage());
            }
        });

        hostButton.setOnAction(e -> {
            String[] address = parseAddress(addressField.getText());
            if (address == null) {
                status.setText("Server must look like host:port, e.g. 192.168.1.10:7777");
                return;
            }
            int port = Integer.parseInt(address[1]);
            try {
                server = new RaceServer(port, sizeBox.getValue());
                server.start();
            } catch (IOException ex) {
                status.setText("Cannot host on port " + port + ": " + ex.getMessage());
                return;
            }
            joinRace("127.0.0.1", port);
            status.setText("Hosting on port " + port + " — your friend joins at "
                    + localAddress() + ":" + port);
        });

        joinButton.setOnAction(e -> {
            String[] address = parseAddress(addressField.getText());
            if (address == null) {
                status.setText("Server must look like host:port, e.g. 192.168.1.10:7777");
                return;
            }
            joinRace(address[0], Integer.parseInt(address[1]));
        });

        nameField.setPrefWidth(120);
        addressField.setPrefWidth(150);
        HBox raceBar = new HBox(8, new Label("Name:"), nameField, new Label("Server:"), addressField,
                hostButton, joinButton);
        raceBar.setAlignment(Pos.CENTER_LEFT);
        raceBar.setPadding(new Insets(0, 10, 10, 10));

        HBox toolbar = new HBox(8, new Label("Size:"), sizeBox, generateButton,
                solveButton, clearButton, saveButton, loadButton, exportButton);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.setPadding(new Insets(10));

        // Only the canvas stays in the traversal chain, so arrow keys can never hand focus to a control.
        for (Node control : List.of(sizeBox, generateButton, solveButton, clearButton, saveButton,
                loadButton, exportButton, hostButton, joinButton, nameField, addressField)) {
            control.setFocusTraversable(false);
        }
        canvas.setFocusTraversable(true);
        canvas.setStyle("-fx-focus-color: transparent; -fx-faint-focus-color: transparent;");

        StackPane center = new StackPane(canvas);
        center.setPadding(new Insets(0, 10, 10, 10));

        status.setPadding(new Insets(0, 12, 12, 12));

        BorderPane root = new BorderPane();
        root.setCenter(center);
        root.setTop(new VBox(toolbar, raceBar));
        root.setBottom(status);
        root.setStyle("-fx-background-color: #F2F3F7;");

        Scene scene = new Scene(root, 1060, 740);
        scene.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.getTarget() instanceof TextInputControl || e.getTarget() instanceof ChoiceBox
                    || scene.getFocusOwner() instanceof TextInputControl) {
                return;
            }
            int dx = 0;
            int dy = 0;
            switch (e.getCode()) {
                case UP -> dy = -1;
                case DOWN -> dy = 1;
                case LEFT -> dx = -1;
                case RIGHT -> dx = 1;
                default -> {
                    return;
                }
            }
            e.consume();
            canvas.requestFocus();
            if (canvas.movePlayer(dx, dy)) {
                moves++;
                if (racing && !raceFinished) {
                    int[] pos = canvas.playerPos();
                    client.sendPos(pos[0], pos[1]);
                    if (canvas.isAtEnd()) {
                        raceFinished = true;
                        client.sendDone(moves);
                        status.setText("You finished in " + moves + " moves — waiting for the other player…");
                        return;
                    }
                }
                status.setText(canvas.isAtEnd()
                        ? "You reached the end in " + moves + " moves!"
                        : "Moves: " + moves);
            }
        });
        stage.setTitle("Maze Creator");
        stage.setScene(scene);
        stage.show();
        canvas.requestFocus();
        this.stage = stage;

        showMaze(Maze.generate(41, 41, new Random()), "Generated 41x41 maze.");
    }

    private void showMaze(Maze maze, String message) {
        canvas.setMaze(maze);
        moves = 0;
        canvas.requestFocus();
        status.setText(message + " Arrow keys move the blue player.");
    }

    private void joinRace(String host, int port) {
        String typed = nameField.getText().trim().replaceAll("\\s+", " ");
        myName = typed.isEmpty() ? "player" : typed;
        racing = true;
        raceFinished = false;
        setRaceControls(true);
        stage.setTitle("Maze Creator — " + myName);
        status.setText("Connecting to " + host + ":" + port + " as " + myName + "…");
        client = new RaceClient(host, port, myName, new RaceClient.Listener() {
            @Override
            public void onOpponent(String name) {
                Platform.runLater(() -> status.setText("Opponent: " + name + ". Waiting for the race maze…"));
            }

            @Override
            public void onMaze(Maze maze) {
                Platform.runLater(() -> {
                    canvas.setMaze(maze);
                    moves = 0;
                    raceFinished = false;
                    status.setText("Race maze loaded. Waiting for both players…");
                });
            }

            @Override
            public void onBegin() {
                Platform.runLater(() -> status.setText("Race! First to reach the red end wins."));
            }

            @Override
            public void onOpponentPos(int x, int y) {
                Platform.runLater(() -> canvas.setOpponent(x, y));
            }

            @Override
            public void onWin(String name, int winnerMoves) {
                Platform.runLater(() -> {
                    status.setText(name.equals(myName)
                            ? "You win the race in " + winnerMoves + " moves!"
                            : name + " wins the race in " + winnerMoves + " moves!");
                    endRace();
                });
            }

            @Override
            public void onOpponentLeft() {
                Platform.runLater(() -> {
                    status.setText("Opponent left — race over.");
                    endRace();
                });
            }

            @Override
            public void onError(String message) {
                Platform.runLater(() -> {
                    status.setText(message);
                    endRace();
                });
            }
        });
        client.start();
    }

    private void endRace() {
        racing = false;
        raceFinished = false;
        if (client != null) {
            client.close();
            client = null;
        }
        if (server != null) {
            server.close();
            server = null;
        }
        setRaceControls(false);
        canvas.clearOpponent();
        if (stage != null) {
            stage.setTitle("Maze Creator");
        }
    }

    private void setRaceControls(boolean inRace) {
        sizeBox.setDisable(inRace);
        generateButton.setDisable(inRace);
        solveButton.setDisable(inRace);
        loadButton.setDisable(inRace);
        hostButton.setDisable(inRace);
        joinButton.setDisable(inRace);
    }

    private static String localAddress() {
        String fallback = null;
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface nic = interfaces.nextElement();
                if (nic.isLoopback() || !nic.isUp() || nic.isVirtual()) {
                    continue;
                }
                String name = nic.getName().toLowerCase();
                boolean bridgeOrContainer = name.startsWith("docker") || name.startsWith("br-")
                        || name.startsWith("veth") || name.startsWith("virbr")
                        || name.startsWith("tun") || name.startsWith("tap");
                for (InterfaceAddress interfaceAddress : nic.getInterfaceAddresses()) {
                    InetAddress address = interfaceAddress.getAddress();
                    if (address instanceof Inet4Address && address.isSiteLocalAddress()) {
                        String ip = address.getHostAddress();
                        if (!bridgeOrContainer) {
                            return ip;
                        }
                        if (fallback == null) {
                            fallback = ip;
                        }
                    }
                }
            }
        } catch (SocketException ignored) {
            // fall through to placeholder
        }
        return fallback != null ? fallback : "<this machine's LAN IP>";
    }

    private static String[] parseAddress(String text) {
        String value = text.trim();
        int colon = value.lastIndexOf(':');
        if (colon < 0) {
            return null;
        }
        String host = value.substring(0, colon);
        if (host.isEmpty()) {
            host = "127.0.0.1";
        }
        try {
            int port = Integer.parseInt(value.substring(colon + 1));
            if (port < 1 || port > 65535) {
                return null;
            }
            return new String[]{host, value.substring(colon + 1)};
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    public void stop() {
        if (client != null) {
            client.close();
        }
        if (server != null) {
            server.close();
        }
    }

    private FileChooser chooser(String title, String extension) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(title);
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(title, extension));
        return chooser;
    }

    public static void main(String[] args) {
        launch(args);
    }
}
