import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Random;
import javax.sound.sampled.*;
import java.awt.image.BufferedImage;

public class PacMan extends JPanel implements ActionListener, KeyListener, MouseListener {
    enum GameMode {
        NORMAL, SPEED, HARDCORE
    }

    public static void main(String[] args) {
        javax.swing.SwingUtilities.invokeLater(() -> {
            javax.swing.JFrame frame = new javax.swing.JFrame("Pac Man");
            PacMan pacmanGame = new PacMan();
            frame.setDefaultCloseOperation(javax.swing.JFrame.EXIT_ON_CLOSE);
            frame.add(pacmanGame);
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setResizable(false);
            frame.setVisible(true);
            pacmanGame.requestFocusInWindow();
        });
    }

    private GameMode currentMode = GameMode.NORMAL;

    class Block {
        int x, y, width, height;
        Image image;
        int startX, startY;
        char direction = 'N', nextDirection = 'N';
        int velocityX = 0, velocityY = 0;
        Image normalImage, scaredImage;
        boolean isScared = false, isEaten = false;
        Timer resetTimer;
        String ghostType;

        Block(Image image, int x, int y, int width, int height, String ghostType) {
            this.image = image;
            this.normalImage = image;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.startX = x;
            this.startY = y;
            this.ghostType = ghostType;
            this.resetTimer = new Timer(3000, e -> resetGhost());
            this.resetTimer.setRepeats(false);
        }

        void updateDirection(char newDirection) {
            this.nextDirection = newDirection;
            tryChangeDirection();
        }

        void tryChangeDirection() {
            if (nextDirection == 'N') return;

            int speed = Math.abs(velocityX) > 0 ? Math.abs(velocityX) : Math.abs(velocityY);
            int margin = tileSize * 3 / 4;

            boolean canTurn = false;
            if (nextDirection == 'L' || nextDirection == 'R') {
                int yMod = y % tileSize;
                canTurn = (yMod <= margin || yMod >= tileSize - margin);
            } else if (nextDirection == 'U' || nextDirection == 'D') {
                int xMod = x % tileSize;
                canTurn = (xMod <= margin || xMod >= tileSize - margin);
            }

            if (!canTurn && (velocityX != 0 || velocityY != 0)) return;

            int testX = x, testY = y;
            if (nextDirection == 'L' || nextDirection == 'R') {
                testY = Math.round((float) y / tileSize) * tileSize;
            } else if (nextDirection == 'U' || nextDirection == 'D') {
                testX = Math.round((float) x / tileSize) * tileSize;
            }

            if (nextDirection == 'U') testY -= tileSize;
            else if (nextDirection == 'D') testY += tileSize;
            else if (nextDirection == 'L') testX -= tileSize;
            else if (nextDirection == 'R') testX += tileSize;

            Block testBlock = new Block(image, testX, testY, width, height, ghostType);
            boolean canMove = true;
            for (Block wall : walls) {
                if (collision(testBlock, wall)) {
                    canMove = false;
                    break;
                }
            }

            if (canMove) {
                if (nextDirection == 'L' || nextDirection == 'R') {
                    y = Math.round((float) y / tileSize) * tileSize;
                } else if (nextDirection == 'U' || nextDirection == 'D') {
                    x = Math.round((float) x / tileSize) * tileSize;
                }
                direction = nextDirection;
                updateVelocity();
            }
        }

        void updateVelocity() {
            int baseSpeed = tileSize / 6;
            double speedMultiplier = currentMode == GameMode.SPEED ? 2.0 : (currentMode == GameMode.HARDCORE ? 1.2 : 1.0);
            if (this == pacman) {
                speedMultiplier *= (1 + 0.05 * (level - 1));
            } else {
                speedMultiplier *= (1 + 0.10 * (level - 1));
                if (ghostType != null && ghostType.equals("Blinky")) {
                    int distance = Math.abs(pacman.x / tileSize - x / tileSize) + Math.abs(pacman.y / tileSize - y / tileSize);
                    if (distance < 5) speedMultiplier *= 1.2; // Speed boost when close
                }
                if (isScared) speedMultiplier *= 0.5;
            }
            int speed = (int) (baseSpeed * speedMultiplier);

            if (direction == 'U') {
                velocityX = 0;
                velocityY = -speed;
            } else if (direction == 'D') {
                velocityX = 0;
                velocityY = speed;
            } else if (direction == 'L') {
                velocityX = -speed;
                velocityY = 0;
            } else if (direction == 'R') {
                velocityX = speed;
                velocityY = 0;
            } else {
                velocityX = 0;
                velocityY = 0;
            }
        }

        void reset() {
            x = startX;
            y = startY;
            isScared = false;
            isEaten = false;
            image = normalImage;
            direction = 'N';
            nextDirection = 'N';
            velocityX = 0;
            velocityY = 0;
            if (resetTimer != null && resetTimer.isRunning()) {
                resetTimer.stop();
            }
        }

        void resetGhost() {
            reset();
            char newDirection = directions[random.nextInt(4)];
            updateDirection(newDirection);
        }
    }

    private int rowCount = 31;
    private int columnCount = 28;
    private int tileSize = 24;
    private int boardWidth = columnCount * tileSize;
    private int boardHeight = rowCount * tileSize;
    private int frameCount = 0;

    private Image wallImage, blueGhostImage, orangeGhostImage, pinkGhostImage, redGhostImage, scaredGhostImage, cherryImage, powerFoodImage;
    private Image pacmanUpImage, pacmanDownImage, pacmanLeftImage, pacmanRightImage;
    private Image[] pacmanAnimUp = new Image[4], pacmanAnimDown = new Image[4], pacmanAnimLeft = new Image[4], pacmanAnimRight = new Image[4];
    private int animationFrame = 0;

    private Clip gameOverSound, pacmanDeadSound, backgroundMusic;

    // Start screen animation variables
    private int startScreenPacmanX = -tileSize;
    private int startScreenGhostX = -tileSize * 5;
    private Timer startScreenTimer;

    private String[] tileMap = {
        "XXXXXXXXXXXXXXXXXXXXXXXXXXXX",
        "X            XX            X",
        "X XXXX XXXXX XX XXXXX XXXX X",
        "X XFXX X   X XX X   X XXFX X",
        "X XXXX X XXX    XXX X XXXX X",
        "X      X XXXX  XXXX X      X",
        "XXXXXX X XX XXXXX XX X XXXXX",
        "XXXXXX X XX       XX X XXXXX",
        "X    X X XX XXXXX XX X X   X",
        "X    XXX XX       XX XXX   X",
        "XXXXXX X XXXXXXXX XX XXXXXXX",
        "     X X XX       XX X      ",
        "XXXX X X XX XXXrXXoX X XXXX ",
        "XXX  O      Xb pX    O   XXX",
        "XXXX X XXXX XXXXXXXX XXX XXX",
        "     X XXXX       XXXX X    ",
        "XXXX X XXXX XXXXXXXX XXX XXX",
        "XXX  X XX       XX XXXX XXXX",
        "X    X XX XXXXX XX X    X  X",
        "X XXXXXXX XX    XX XXXXXXX X",
        "X XXXXXXX XX    XX XXXXXXX X",
        "X      XX XXXXX XX XX      X",
        "XXXXXX XX XX  F XX XX XXXXXX",
        "XXXXXX XX XX    XX XX XXXXXX",
        "X    X XX XXXXXXXX XX X    X",
        "X    X XX          XX X    X",
        "X XXXX XXXX      XXXX XXXX X",
        "X XF X    X  P   X    X FX X",
        "X XXXX XXXX      XXXX XXXX X",
        "X                          X",
        "XXXXXXXXXXXXXXXXXXXXXXXXXXXX"
    };

    HashSet<Block> walls, foods, ghosts;
    Block pacman, bonusFruit = null;
    int bonusTimer = 0, scaredTimer = 0, scatterTimer = 0, levelTransitionTimer = 0;
    boolean scaredState = false, scatterMode = true, inLevelTransition = false;
    Timer gameLoop, animationTimer, levelTransition;
    char[] directions = {'U', 'D', 'L', 'R'};
    Random random = new Random();
    int score = 0, lives = 3, level = 1;
    HashMap<GameMode, Integer> highScores = new HashMap<>();
    boolean gameOver = false, gameStarted = false, modeSelection = true, isPaused = false;
    private Block collisionTestBlock;

    public PacMan() {
        setPreferredSize(new Dimension(boardWidth, boardHeight + 50));
        setBackground(Color.BLACK);
        addKeyListener(this);
        addMouseListener(this);
        setFocusable(true);
        requestFocusInWindow();

        try {
            wallImage = loadImage("wall.png", Color.BLUE);
            blueGhostImage = loadImage("blueGhost.png", Color.CYAN);
            orangeGhostImage = loadImage("orangeGhost.png", Color.ORANGE);
            pinkGhostImage = loadImage("pinkGhost.png", Color.PINK);
            redGhostImage = loadImage("redGhost.png", Color.RED);
            scaredGhostImage = loadImage("scaredGhost.png", Color.BLUE);
            cherryImage = loadImage("cherry.png", Color.RED);
            powerFoodImage = loadImage("powerFood.png", Color.WHITE);
            pacmanUpImage = loadImage("pacmanUp.png", Color.YELLOW);
            pacmanDownImage = loadImage("pacmanDown.png", Color.YELLOW);
            pacmanLeftImage = loadImage("pacmanLeft.png", Color.YELLOW);
            pacmanRightImage = loadImage("pacmanRight.png", Color.YELLOW);
        } catch (Exception e) {
            System.err.println("Error loading images: " + e.getMessage());
        }

        try {
            gameOverSound = loadSound("game_over.wav");
            pacmanDeadSound = loadSound("pacman-is-dead.wav");
            backgroundMusic = loadSound("playing-pac-man.wav");
        } catch (Exception e) {
            System.err.println("Error loading sounds: " + e.getMessage());
        }

        for (int i = 0; i < 4; i++) {
            pacmanAnimUp[i] = pacmanUpImage;
            pacmanAnimDown[i] = pacmanDownImage;
            pacmanAnimLeft[i] = pacmanLeftImage;
            pacmanAnimRight[i] = pacmanRightImage;
        }

        highScores.put(GameMode.NORMAL, 0);
        highScores.put(GameMode.SPEED, 0);
        highScores.put(GameMode.HARDCORE, 0);

        loadMap();
        configureGameMode();

        collisionTestBlock = new Block(null, 0, 0, tileSize, tileSize, null);

        for (Block ghost : ghosts) {
            ghost.scaredImage = scaredGhostImage;
            char newDirection = directions[random.nextInt(4)];
            ghost.updateDirection(newDirection);
        }

        int timerDelay = currentMode == GameMode.SPEED ? 40 : 50;
        gameLoop = new Timer(timerDelay, this);
        animationTimer = new Timer(100, e -> {
            animationFrame = (animationFrame + 1) % 4;
            updatePacmanImage();
        });
        levelTransition = new Timer(2000, e -> {
            inLevelTransition = false;
            levelTransition.stop();
            if (!gameOver && gameStarted && !isPaused) {
                gameLoop.start();
                animationTimer.start();
            }
        });
        levelTransition.setRepeats(false);

        // Initialize start screen animation timer
        startScreenTimer = new Timer(50, e -> {
            startScreenPacmanX += 5;
            startScreenGhostX += 5;
            if (startScreenPacmanX > boardWidth) {
                startScreenPacmanX = -tileSize;
                startScreenGhostX = -tileSize * 5;
            }
            repaint();
        });
        startScreenTimer.start();

        if (currentMode == GameMode.HARDCORE) {
            lives = 1;
        }
    }

    private Image loadImage(String fileName, Color fallback) throws Exception {
        java.net.URL imgURL = getClass().getResource("/" + fileName);
        if (imgURL == null) {
            System.err.println("Image not found: " + fileName + ", using fallback color");
            BufferedImage img = new BufferedImage(tileSize, tileSize, BufferedImage.TYPE_INT_ARGB);
            Graphics g = img.getGraphics();
            g.setColor(fallback);
            g.fillRect(0, 0, tileSize, tileSize);
            g.dispose();
            return img;
        }
        return new ImageIcon(imgURL).getImage();
    }

    private Clip loadSound(String fileName) throws Exception {
        String resourcePath = "/" + fileName;
        System.out.println("Attempting to load sound: " + resourcePath);
        java.net.URL soundURL = getClass().getResource(resourcePath);
        if (soundURL == null) {
            System.err.println("Sound file not found: " + fileName);
            return null;
        }
        System.out.println("Sound file found at: " + soundURL);
        AudioInputStream audioIn = AudioSystem.getAudioInputStream(soundURL);

        // Check the format and convert if necessary
        AudioFormat originalFormat = audioIn.getFormat();
        AudioFormat targetFormat = new AudioFormat(
            AudioFormat.Encoding.PCM_SIGNED,
            originalFormat.getSampleRate(),
            16,
            originalFormat.getChannels(),
            originalFormat.getChannels() * 2,
            originalFormat.getSampleRate(),
            false
        );

        if (!AudioSystem.isConversionSupported(targetFormat, originalFormat)) {
            System.err.println("Cannot convert audio format for: " + fileName);
            return null;
        }

        AudioInputStream convertedStream = AudioSystem.getAudioInputStream(targetFormat, audioIn);
        Clip clip = AudioSystem.getClip();
        clip.open(convertedStream);
        return clip;
    }

    void updatePacmanImage() {
        if (!gameStarted || gameOver) return;
        switch (pacman.direction) {
            case 'U': pacman.image = pacmanAnimUp[animationFrame]; break;
            case 'D': pacman.image = pacmanAnimDown[animationFrame]; break;
            case 'L': pacman.image = pacmanAnimLeft[animationFrame]; break;
            case 'R': pacman.image = pacmanAnimRight[animationFrame]; break;
            default: pacman.image = pacmanRightImage;
        }
    }

    public void loadMap() {
        walls = new HashSet<>();
        foods = new HashSet<>();
        ghosts = new HashSet<>();

        for (int r = 0; r < rowCount; r++) {
            for (int c = 0; c < columnCount; c++) {
                char tileMapChar = tileMap[r].charAt(c);
                int x = c * tileSize;
                int y = r * tileSize;

                if (tileMapChar == 'X') {
                    walls.add(new Block(wallImage, x, y, tileSize, tileSize, null));
                } else if (tileMapChar == 'b') {
                    ghosts.add(new Block(blueGhostImage, x, y, tileSize, tileSize, "Inky"));
                } else if (tileMapChar == 'o') {
                    ghosts.add(new Block(orangeGhostImage, x, y, tileSize, tileSize, "Clyde"));
                } else if (tileMapChar == 'p') {
                    ghosts.add(new Block(pinkGhostImage, x, y, tileSize, tileSize, "Pinky"));
                } else if (tileMapChar == 'r') {
                    ghosts.add(new Block(redGhostImage, x, y, tileSize, tileSize, "Blinky"));
                } else if (tileMapChar == 'P') {
                    pacman = new Block(pacmanRightImage, x, y, tileSize, tileSize, null);
                } else if (tileMapChar == ' ') {
                    foods.add(new Block(null, x + 10, y + 10, 4, 4, null));
                } else if (tileMapChar == 'F') {
                    foods.add(new Block(powerFoodImage, x + 4, y + 4, 16, 16, null));
                }
            }
        }
    }

    private void configureGameMode() {
        for (Block food : foods) {
            if (food.image == powerFoodImage) {
                food.image = powerFoodImage;
            }
        }

        switch (currentMode) {
            case NORMAL:
                break;
            case SPEED:
                for (Block food : foods) {
                    if (food.image == null) {
                        food.width = 6;
                        food.height = 6;
                    } else if (food.image == powerFoodImage) {
                        food.width = 18;
                        food.height = 18;
                    }
                }
                break;
            case HARDCORE:
                for (Block ghost : ghosts) {
                    ghost.updateVelocity();
                }
                break;
        }
    }

    private void updateGhostMovement(Block ghost) {
        if (ghost.isEaten) return;

        char bestDirection = 'N';
        int minDistance = Integer.MAX_VALUE;
        int maxDistance = Integer.MIN_VALUE;

        int pacmanGridX = pacman.x / tileSize;
        int pacmanGridY = pacman.y / tileSize;
        int ghostGridX = ghost.x / tileSize;
        int ghostGridY = ghost.y / tileSize;

        // Check if ghost is in ghost house
        boolean inGhostHouse = (ghostGridY >= 13 && ghostGridY <= 15 && ghostGridX >= 10 && ghostGridX <= 15);
        if (inGhostHouse && !scaredState) {
            // Prioritize moving up to exit ghost house
            if (canMoveGhost(ghostGridX, ghostGridY - 1)) {
                bestDirection = 'U';
            } else {
                // Try left or right if up is blocked
                if (canMoveGhost(ghostGridX - 1, ghostGridY)) bestDirection = 'L';
                else if (canMoveGhost(ghostGridX + 1, ghostGridY)) bestDirection = 'R';
            }
            if (bestDirection != 'N') {
                ghost.updateDirection(bestDirection);
                return;
            }
        }

        ArrayList<Character> validDirections = new ArrayList<>();
        ArrayList<Integer> distances = new ArrayList<>();
        for (char dir : directions) {
            int testX = ghostGridX, testY = ghostGridY;
            if (dir == 'U') testY--;
            else if (dir == 'D') testY++;
            else if (dir == 'L') testX--;
            else if (dir == 'R') testX++;

            if (canMoveGhost(testX, testY) && !isDeadEnd(testX, testY, dir)) {
                validDirections.add(dir);
            }
        }

        if (validDirections.isEmpty()) return;

        if (ghost.isScared) {
            // Flee from Pac-Man by maximizing distance
            for (char dir : validDirections) {
                int testX = ghostGridX, testY = ghostGridY;
                if (dir == 'U') testY--;
                else if (dir == 'D') testY++;
                else if (dir == 'L') testX--;
                else if (dir == 'R') testX++;

                int distance = Math.abs(testX - pacmanGridX) + Math.abs(testY - pacmanGridY);
                if (distance > maxDistance) {
                    maxDistance = distance;
                    bestDirection = dir;
                }
            }
            // Small chance to recover early on higher levels
            if (level > 5 && random.nextDouble() < 0.02) {
                ghost.isScared = false;
                ghost.updateVelocity();
            }
        } else if (scatterMode) {
            int targetX, targetY;
            switch (ghost.ghostType) {
                case "Blinky": targetX = columnCount - 2; targetY = 1; break;
                case "Pinky": targetX = 1; targetY = 1; break;
                case "Inky": targetX = columnCount - 2; targetY = rowCount - 2; break;
                case "Clyde": targetX = 1; targetY = rowCount - 2; break;
                default: targetX = ghostGridX; targetY = ghostGridY;
            }

            for (char dir : validDirections) {
                int testX = ghostGridX, testY = ghostGridY;
                if (dir == 'U') testY--;
                else if (dir == 'D') testY++;
                else if (dir == 'L') testX--;
                else if (dir == 'R') testX++;

                int distance = Math.abs(testX - targetX) + Math.abs(testY - targetY);
                distances.add(distance);
                if (distance < minDistance) {
                    minDistance = distance;
                    bestDirection = dir;
                }
            }
        } else {
            int targetX = pacmanGridX, targetY = pacmanGridY;
            if (ghost.ghostType.equals("Pinky")) {
                // Predict Pac-Man's position, clamping to map boundaries
                if (pacman.direction == 'U') targetY = Math.max(0, targetY - 4);
                else if (pacman.direction == 'D') targetY = Math.min(rowCount - 1, targetY + 4);
                else if (pacman.direction == 'L') targetX = Math.max(0, targetX - 4);
                else if (pacman.direction == 'R') targetX = Math.min(columnCount - 1, targetX + 4);
            } else if (ghost.ghostType.equals("Inky")) {
                Block blinky = null;
                for (Block g : ghosts) {
                    if (g.ghostType.equals("Blinky")) {
                        blinky = g;
                        break;
                    }
                }
                if (blinky != null) {
                    int pivotX = pacmanGridX, pivotY = pacmanGridY;
                    if (pacman.direction == 'U') pivotY -= 2 + (level % 2);
                    else if (pacman.direction == 'D') pivotY += 2 + (level % 2);
                    else if (pacman.direction == 'L') pivotX -= 2 + (level % 2);
                    else if (pacman.direction == 'R') pivotX += 2 + (level % 2);
                    targetX = Math.min(columnCount - 1, Math.max(0, blinky.x / tileSize + (pivotX - blinky.x / tileSize) * 2));
                    targetY = Math.min(rowCount - 1, Math.max(0, blinky.y / tileSize + (pivotY - blinky.y / tileSize) * 2));
                }
            } else if (ghost.ghostType.equals("Clyde")) {
                int distance = Math.abs(pacmanGridX - ghostGridX) + Math.abs(pacmanGridY - ghostGridY);
                int threshold = Math.max(8 - level / 2, 4);
                if (distance < threshold) {
                    targetX = 1;
                    targetY = rowCount - 2;
                }
            } else if (ghost.ghostType.equals("Blinky")) {
                // Blinky gets faster when close to Pac-Man
                int distance = Math.abs(pacmanGridX - ghostGridX) + Math.abs(pacmanGridY - ghostGridY);
                if (distance < 5) {
                    ghost.updateVelocity(); // Recalculate with a slight speed boost
                }
            }

            for (char dir : validDirections) {
                int testX = ghostGridX, testY = ghostGridY;
                if (dir == 'U') testY--;
                else if (dir == 'D') testY++;
                else if (dir == 'L') testX--;
                else if (dir == 'R') testX++;

                int distance = Math.abs(testX - targetX) + Math.abs(testY - targetY);
                distances.add(distance);
                if (distance < minDistance) {
                    minDistance = distance;
                    bestDirection = dir;
                }
            }
        }

        // Add slight randomness to direction choice among close distances
        if (!distances.isEmpty()) {
            ArrayList<Character> bestDirections = new ArrayList<>();
            for (int i = 0; i < distances.size(); i++) {
                if (distances.get(i) <= minDistance + 1) {
                    bestDirections.add(validDirections.get(i));
                }
            }
            if (!bestDirections.isEmpty()) {
                bestDirection = bestDirections.get(random.nextInt(bestDirections.size()));
            }
        }

        if (bestDirection != 'N') {
            ghost.updateDirection(bestDirection);
        }
    }

    private boolean canMoveGhost(int gridX, int gridY) {
        if (gridX < 0 || gridX >= columnCount || gridY < 0 || gridY >= rowCount) return false;
        Block testBlock = new Block(null, gridX * tileSize, gridY * tileSize, tileSize, tileSize, null);
        for (Block wall : walls) {
            if (collision(testBlock, wall)) {
                return false;
            }
        }
        return true;
    }

    private boolean isDeadEnd(int gridX, int gridY, char direction) {
        int testX = gridX, testY = gridY;
        if (direction == 'U') testY--;
        else if (direction == 'D') testY++;
        else if (direction == 'L') testX--;
        else if (direction == 'R') testX++;

        if (!canMoveGhost(testX, testY)) return true;

        int openDirections = 0;
        if (canMoveGhost(testX, testY - 1) && direction != 'D') openDirections++;
        if (canMoveGhost(testX, testY + 1) && direction != 'U') openDirections++;
        if (canMoveGhost(testX - 1, testY) && direction != 'R') openDirections++;
        if (canMoveGhost(testX + 1, testY) && direction != 'L') openDirections++;

        return openDirections <= 1;
    }

    private void drawLives(Graphics g) {
        int iconSize = 20;
        int startX = 10;
        int y = boardHeight + 30;
        for (int i = 0; i < lives; i++) {
            g.drawImage(pacmanRightImage, startX + i * (iconSize + 5), y - iconSize, iconSize, iconSize, this);
        }
    }

    private void togglePause() {
        isPaused = !isPaused;
        if (isPaused) {
            gameLoop.stop();
            animationTimer.stop();
            if (backgroundMusic != null) {
                backgroundMusic.stop();
            }
        } else {
            gameLoop.start();
            animationTimer.start();
            if (backgroundMusic != null) {
                backgroundMusic.setFramePosition(0);
                backgroundMusic.loop(Clip.LOOP_CONTINUOUSLY);
            }
        }
        repaint();
    }

    private void resetGame(boolean newGame) {
        // Stop all sounds
        if (backgroundMusic != null) backgroundMusic.stop();
        if (gameOverSound != null) gameOverSound.stop();
        if (pacmanDeadSound != null) pacmanDeadSound.stop();

        score = 0;
        lives = currentMode == GameMode.HARDCORE ? 1 : 3;
        level = 1;
        gameOver = false;
        gameStarted = newGame;
        modeSelection = !newGame;
        scaredTimer = 0;
        scatterTimer = 0;
        scaredState = false;
        scatterMode = true;
        bonusTimer = 0;
        bonusFruit = null;
        frameCount = 0;
        loadMap();
        configureGameMode();
        resetPositions();
        if (newGame) {
            gameLoop.start();
            animationTimer.start();
            if (backgroundMusic != null) {
                backgroundMusic.setFramePosition(0);
                backgroundMusic.loop(Clip.LOOP_CONTINUOUSLY);
            }
        } else {
            gameLoop.stop();
            animationTimer.stop();
        }
        requestFocusInWindow();
    }

    public void paintComponent(Graphics g) {
        super.paintComponent(g);
        draw(g);
    }

    public void draw(Graphics g) {
        if (modeSelection) {
            g.setColor(Color.YELLOW);
            g.setFont(new Font("Arial", Font.BOLD, 36));
            g.drawString("PAC-MAN", boardWidth / 2 - 80, boardHeight / 2 - 100);
            g.setFont(new Font("Arial", Font.PLAIN, 24));
            g.drawString("Select Mode:", boardWidth / 2 - 70, boardHeight / 2 - 50);
            g.drawString("1. Normal Mode", boardWidth / 2 - 80, boardHeight / 2);
            g.drawString("2. Speed Mode", boardWidth / 2 - 80, boardHeight / 2 + 40);
            g.drawString("3. Hardcore Mode", boardWidth / 2 - 80, boardHeight / 2 + 80);
            g.setFont(new Font("Arial", Font.PLAIN, 18));
            g.drawString("High Score: " + highScores.get(currentMode), boardWidth / 2 - 70, boardHeight / 2 + 120);
            requestFocusInWindow();
            return;
        }
        if (!gameStarted) {
            g.setColor(Color.YELLOW);
            g.setFont(new Font("Arial", Font.BOLD, 36));
            g.drawString("PAC-MAN", boardWidth / 2 - 80, boardHeight / 2 - 50);
            g.setFont(new Font("Arial", Font.PLAIN, 18));
            g.drawString("Press any key to start", boardWidth / 2 - 90, boardHeight / 2);
            g.drawString("High Score: " + highScores.get(currentMode), boardWidth / 2 - 70, boardHeight / 2 + 40);

            // Draw animated Pac-Man and ghost on the start screen
            if (pacmanRightImage != null) {
                g.drawImage(pacmanRightImage, startScreenPacmanX, boardHeight / 2 - 100, tileSize, tileSize, this);
            }
            if (redGhostImage != null) {
                g.drawImage(redGhostImage, startScreenGhostX, boardHeight / 2 - 100, tileSize, tileSize, this);
            }

            requestFocusInWindow();
            return;
        }

        if (gameOver) {
            g.setColor(Color.RED);
            g.setFont(new Font("Arial", Font.BOLD, 48));
            g.drawString("GAME OVER", boardWidth / 2 - 150, boardHeight / 2 - 100);
            g.setColor(Color.WHITE);
            g.setFont(new Font("Arial", Font.PLAIN, 24));
            g.drawString("Score: " + score, boardWidth / 2 - 50, boardHeight / 2 - 40);
            g.setFont(new Font("Arial", Font.PLAIN, 18));
            g.drawString("C: Continue", boardWidth / 2 - 80, boardHeight / 2 + 40);
            g.drawString("E: Exit", boardWidth / 2 - 80, boardHeight / 2 + 70);
            g.drawString("M: Back to Menu", boardWidth / 2 - 80, boardHeight / 2 + 100);
            requestFocusInWindow();
            return;
        }

        if (isPaused) {
            g.setColor(Color.YELLOW);
            g.setFont(new Font("Arial", Font.BOLD, 36));
            g.drawString("PAUSED", boardWidth / 2 - 80, boardHeight / 2);
            g.setFont(new Font("Arial", Font.PLAIN, 18));
            g.drawString("Press any key to continue", boardWidth / 2 - 90, boardHeight / 2 + 40);
            requestFocusInWindow();
            return;
        }

        if (inLevelTransition) {
            g.setColor(Color.YELLOW);
            g.setFont(new Font("Arial", Font.BOLD, 36));
            g.drawString("Level " + level, boardWidth / 2 - 80, boardHeight / 2);
            return;
        }

        for (Block wall : walls) {
            if (wall.image != null) {
                g.drawImage(wall.image, wall.x, wall.y, wall.width, wall.height, this);
            } else {
                g.setColor(Color.BLUE);
                g.fillRect(wall.x, wall.y, wall.width, wall.height);
            }
        }

        for (Block food : foods) {
            if (!food.isEaten) {
                if (food.image != null) {
                    g.drawImage(food.image, food.x, food.y, food.width, food.height, this);
                } else {
                    g.setColor(Color.WHITE);
                    g.fillOval(food.x, food.y, food.width, food.height);
                }
            }
        }

        if (bonusFruit != null && !bonusFruit.isEaten) {
            if (bonusFruit.image != null) {
                g.drawImage(bonusFruit.image, bonusFruit.x, bonusFruit.y, bonusFruit.width, bonusFruit.height, this);
            } else {
                g.setColor(Color.RED);
                g.fillOval(bonusFruit.x, bonusFruit.y, bonusFruit.width, bonusFruit.height);
            }
        }

        for (Block ghost : ghosts) {
            if (!ghost.isEaten) {
                Image img = ghost.isScared ? ghost.scaredImage : ghost.image;
                if (img != null) {
                    g.drawImage(img, ghost.x, ghost.y, ghost.width, ghost.height, this);
                } else {
                    g.setColor(ghost.isScared ? Color.BLUE : Color.RED);
                    g.fillOval(ghost.x, ghost.y, ghost.width, ghost.height);
                }
            }
        }

        if (!gameOver) {
            if (pacman.image != null) {
                g.drawImage(pacman.image, pacman.x, pacman.y, pacman.width, pacman.height, this);
            } else {
                g.setColor(Color.YELLOW);
                g.fillOval(pacman.x, pacman.y, pacman.width, pacman.height);
            }
        }

        g.setColor(Color.WHITE);
        g.setFont(new Font("Arial", Font.PLAIN, 18));
        g.drawString("Score: " + score, 10, boardHeight + 20);
        drawLives(g);
    }

    boolean collision(Block a, Block b) {
        return a.x < b.x + b.width && a.x + a.width > b.x &&
               a.y < b.y + b.height && a.y + a.height > b.y;
    }

    void moveBlock(Block block) {
        if (block == null) return;

        int newX = block.x + block.velocityX;
        int newY = block.y + block.velocityY;

        if (newX < -tileSize) {
            newX = (columnCount - 1) * tileSize;
        } else if (newX >= columnCount * tileSize) {
            newX = -tileSize;
        }

        collisionTestBlock.x = newX;
        collisionTestBlock.y = newY;
        boolean hitWall = false;
        for (Block wall : walls) {
            if (collision(collisionTestBlock, wall)) {
                hitWall = true;
                break;
            }
        }

        if (!hitWall) {
            block.x = newX;
            block.y = newY;
        } else {
            block.velocityX = 0;
            block.velocityY = 0;
            if (block == pacman && block.nextDirection != 'N' && block.nextDirection != block.direction) {
                block.tryChangeDirection();
            }
        }
    }

    void updateGame() {
        if (gameStarted && !gameOver && !isPaused && !inLevelTransition) {
            if (!gameLoop.isRunning()) {
                gameLoop.start();
            }
            if (!animationTimer.isRunning()) {
                animationTimer.start();
            }
        }

        if (!gameStarted || gameOver || isPaused || inLevelTransition) return;

        frameCount++;

        int scatterDuration = currentMode == GameMode.HARDCORE ? 100 : Math.max(150 - level * 10, 50);
        int chaseDuration = currentMode == GameMode.HARDCORE ? 200 : Math.max(300 - level * 20, 100);
        scatterTimer++;
        if (scatterTimer < scatterDuration) {
            scatterMode = true;
        } else if (scatterTimer < scatterDuration + chaseDuration) {
            scatterMode = false;
        } else {
            scatterTimer = 0;
        }

        pacman.tryChangeDirection();
        moveBlock(pacman);

        ArrayList<Block> eatenFoods = new ArrayList<>();
        for (Block food : foods) {
            if (!food.isEaten && collision(pacman, food)) {
                food.isEaten = true;
                if (food.image == powerFoodImage) {
                    score += currentMode == GameMode.SPEED ? 75 : 50;
                    scaredState = true;
                    scaredTimer = 0;
                    for (Block ghost : ghosts) {
                        ghost.isScared = true;
                        ghost.updateVelocity();
                    }
                } else {
                    score += currentMode == GameMode.SPEED ? 15 : 10;
                }
                eatenFoods.add(food);
            }
        }

        if (bonusFruit != null && !bonusFruit.isEaten && collision(pacman, bonusFruit)) {
            bonusFruit.isEaten = true;
            score += 100 * level;
            bonusTimer = 0;
        }

        if (scaredState) {
            scaredTimer++;
            int scaredDuration = currentMode == GameMode.SPEED ? 150 : (currentMode == GameMode.HARDCORE ? 100 : 200);
            if (scaredTimer > scaredDuration) {
                scaredState = false;
                for (Block ghost : ghosts) {
                    ghost.isScared = false;
                    ghost.updateVelocity();
                }
            }
        }

        if (bonusFruit == null && (frameCount == 1000 || frameCount == 2000)) {
            bonusFruit = new Block(cherryImage, (columnCount / 2) * tileSize, (rowCount / 2) * tileSize, tileSize, tileSize, null);
            bonusTimer = 0;
        }
        if (bonusFruit != null && !bonusFruit.isEaten) {
            bonusTimer++;
            if (bonusTimer > 300) {
                bonusFruit = null;
                bonusTimer = 0;
            }
        }

        for (Block ghost : ghosts) {
            if (ghost.isEaten) continue;
            updateGhostMovement(ghost);
            moveBlock(ghost);

            if (collision(pacman, ghost)) {
                if (ghost.isScared) {
                    ghost.isEaten = true;
                    score += 200;
                    ghost.resetTimer.start();
                } else {
                    lives--;
                    if (pacmanDeadSound != null) {
                        pacmanDeadSound.stop();
                        pacmanDeadSound.setFramePosition(0);
                        pacmanDeadSound.start();
                    }
                    if (lives <= 0) {
                        gameOver = true;
                        if (backgroundMusic != null) {
                            backgroundMusic.stop();
                        }
                        if (gameOverSound != null) {
                            gameOverSound.stop();
                            gameOverSound.setFramePosition(0);
                            gameOverSound.start();
                        }
                        gameLoop.stop();
                        animationTimer.stop();
                        for (Block g : ghosts) {
                            if (g.resetTimer.isRunning()) {
                                g.resetTimer.stop();
                            }
                        }
                        highScores.put(currentMode, Math.max(score, highScores.get(currentMode)));
                        repaint();
                    } else {
                        resetPositions();
                        scaredState = false;
                        scaredTimer = 0;
                        scatterTimer = 0;
                        scatterMode = true;
                    }
                }
            }
        }

        boolean allEaten = true;
        for (Block food : foods) {
            if (!food.isEaten) {
                allEaten = false;
                break;
            }
        }
        if (allEaten) {
            level++;
            inLevelTransition = true;
            levelTransition.start();
            lives = currentMode == GameMode.HARDCORE ? 1 : Math.min(lives + 1, 5);
            loadMap();
            configureGameMode();
            resetPositions();
            scaredState = false;
            scaredTimer = 0;
            scatterTimer = 0;
            scatterMode = true;
        }
    }

    void resetPositions() {
        pacman.reset();
        for (Block ghost : ghosts) {
            ghost.reset();
            char newDirection = directions[random.nextInt(4)];
            ghost.updateDirection(newDirection);
        }
    }

    char getOppositeDirection(char dir) {
        switch (dir) {
            case 'U': return 'D';
            case 'D': return 'U';
            case 'L': return 'R';
            case 'R': return 'L';
            default: return 'N';
        }
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        updateGame();
        repaint();
    }

    @Override
    public void keyPressed(KeyEvent e) {
        if (gameOver) {
            switch (e.getKeyCode()) {
                case KeyEvent.VK_C:
                    resetGame(true);
                    if (backgroundMusic != null) {
                        backgroundMusic.setFramePosition(0);
                        backgroundMusic.loop(Clip.LOOP_CONTINUOUSLY);
                    }
                    break;
                case KeyEvent.VK_E:
                    if (backgroundMusic != null) backgroundMusic.stop();
                    if (gameOverSound != null) gameOverSound.stop();
                    if (pacmanDeadSound != null) pacmanDeadSound.stop();
                    System.exit(0);
                    break;
                case KeyEvent.VK_M:
                    resetGame(false);
                    break;
            }
            return;
        }

        if (isPaused || !gameStarted) {
            if (e.getKeyCode() == KeyEvent.VK_SPACE || !isPaused) {
                if (modeSelection) {
                    switch (e.getKeyCode()) {
                        case KeyEvent.VK_1:
                            currentMode = GameMode.NORMAL;
                            lives = 3;
                            modeSelection = false;
                            gameStarted = true;
                            startScreenTimer.stop(); // Stop the start screen animation
                            gameLoop.start();
                            animationTimer.start();
                            if (backgroundMusic != null) {
                                backgroundMusic.setFramePosition(0);
                                backgroundMusic.loop(Clip.LOOP_CONTINUOUSLY);
                            }
                            break;
                        case KeyEvent.VK_2:
                            currentMode = GameMode.SPEED;
                            lives = 3;
                            modeSelection = false;
                            gameStarted = true;
                            startScreenTimer.stop(); // Stop the start screen animation
                            gameLoop.start();
                            animationTimer.start();
                            if (backgroundMusic != null) {
                                backgroundMusic.setFramePosition(0);
                                backgroundMusic.loop(Clip.LOOP_CONTINUOUSLY);
                            }
                            break;
                        case KeyEvent.VK_3:
                            currentMode = GameMode.HARDCORE;
                            lives = 1;
                            modeSelection = false;
                            gameStarted = true;
                            startScreenTimer.stop(); // Stop the start screen animation
                            gameLoop.start();
                            animationTimer.start();
                            if (backgroundMusic != null) {
                                backgroundMusic.setFramePosition(0);
                                backgroundMusic.loop(Clip.LOOP_CONTINUOUSLY);
                            }
                            break;
                    }
                } else {
                    gameStarted = true;
                    startScreenTimer.stop(); // Stop the start screen animation
                    gameLoop.start();
                    animationTimer.start();
                    if (backgroundMusic != null) {
                        backgroundMusic.setFramePosition(0);
                        backgroundMusic.loop(Clip.LOOP_CONTINUOUSLY);
                    }
                    if (isPaused) {
                        togglePause();
                    }
                }
                repaint();
                requestFocusInWindow();
            }
        } else {
            switch (e.getKeyCode()) {
                case KeyEvent.VK_UP:
                    pacman.updateDirection('U');
                    break;
                case KeyEvent.VK_DOWN:
                    pacman.updateDirection('D');
                    break;
                case KeyEvent.VK_LEFT:
                    pacman.updateDirection('L');
                    break;
                case KeyEvent.VK_RIGHT:
                    pacman.updateDirection('R');
                    break;
                case KeyEvent.VK_SPACE:
                    togglePause();
                    break;
            }
        }
    }

    @Override
    public void keyReleased(KeyEvent e) {
    }

    @Override
    public void keyTyped(KeyEvent e) {
    }

    @Override
    public void mouseClicked(MouseEvent e) {
        requestFocusInWindow();
    }

    @Override
    public void mousePressed(MouseEvent e) {
    }

    @Override
    public void mouseReleased(MouseEvent e) {
    }

    @Override
    public void mouseEntered(MouseEvent e) {
    }

    @Override
    public void mouseExited(MouseEvent e) {
    }
}