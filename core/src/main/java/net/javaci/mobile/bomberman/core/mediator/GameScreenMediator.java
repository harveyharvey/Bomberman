package net.javaci.mobile.bomberman.core.mediator;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.Vector2;
import net.javaci.mobile.bomberman.core.BomberManGame;
import net.javaci.mobile.bomberman.core.models.BombModel;
import net.javaci.mobile.bomberman.core.net.NetworkInterface;
import net.javaci.mobile.bomberman.core.net.NetworkListenerAdapter;
import net.javaci.mobile.bomberman.core.net.models.RoomModel;
import net.javaci.mobile.bomberman.core.net.protocol.*;
import net.javaci.mobile.bomberman.core.server.GameServer;
import net.javaci.mobile.bomberman.core.session.UserSession;
import net.javaci.mobile.bomberman.core.util.Log;
import net.javaci.mobile.bomberman.core.view.BomberManScreen;
import net.javaci.mobile.bomberman.core.view.GameScreen;

public class GameScreenMediator extends BomberManMediator {

    private GameServer gameServer;
    private NetworkInterface networkInterface;
    private CommandFactory commandFactory = new CommandFactory();
    private GameScreen gameScreen;
    private RoomModel room;
    private int level = 1;

    public GameScreenMediator(BomberManGame game, NetworkInterface networkInterface) {
        super(game);
        this.networkInterface = networkInterface;
        networkInterface.addNetworkListener(new NetworkListenerAdapter() {
            @Override
            public void onMessageReceived(String from, String message) {
                Command command = commandFactory.createCommand(message);
                if (command == null) {
                    Log.d("Waiting split message");
                    return;
                }
                switch (command.getCommand()) {
                    case Command.CREATE_GAME:
                        handleCreateGameCommand((CreateGameCommand)command);
                        break;
                    case Command.MOVE_START:
                        handleMoveStartCommand((MoveCommand) command);
                        break;
                    case Command.MOVE_END:
                        handleMoveEndCommand((MoveEndCommand) command);
                        break;
                    case Command.MOVE_GHOST:
                        handleMoveGhostCommand((MoveGhostCommand) command);
                        break;
                    case Command.GAME_END:
                        handleGameEndCommand((GameEndCommand) command);
                        break;
                    case Command.DROP_BOMB:
                        handleDropBombCommand((DropBombCommand) command);
                        break;
                    case Command.EXPLODE_BOMB:
                        handleExplodeBombCommand((ExplodeBombCommand) command);
                        break;

                    default:
                        break;
                }
            }

            @Override
            public void onPlayerLeftRoom(RoomModel room, String playerName) {
                super.onPlayerLeftRoom(room, playerName);
                if (GameScreenMediator.this.room.equals(room) && !playerName.equals(UserSession.getInstance().getUsername())) {
                    GameScreenMediator.this.onPlayerLeftRoom(playerName);
                }
            }

            @Override
            public void onPlayerJoinedRoom(RoomModel room, String playerName) {
                super.onPlayerJoinedRoom(room, playerName);
                if (GameScreenMediator.this.room.equals(room) && !playerName.equals(UserSession.getInstance().getUsername())) {
                    GameScreenMediator.this.onPlayerJoinedRoom(playerName);
                }
            }

            @Override
            public void onRoomInfoReceived(String[] players, String data) {
                if (players != null) {
                    for (String player: players) {
                        GameScreenMediator.this.onPlayerJoinedRoom(player);
                    }
                }
            }
        });
    }


    @Override
    protected void onScreenShow() {
        super.onScreenShow();
        if ( ! UserSession.getInstance().isServer()) {
            networkInterface.getRoomInfo(UserSession.getInstance().getRoom().getId());
        }
    }

    private void handleExplodeBombCommand(ExplodeBombCommand command) {
        if (command.getFromUser().equals(UserSession.getInstance().getUsername())) {
            return;
        }

//        BombModel found = null;
//        for (BombModel bombModel : gameScreen.getWorld().getBombList()) {
//            if (bombModel.getId() == command.getId()) {
//                found = bombModel;
//                break;
//            }
//        }
//
//        if (found != null) {
//            gameScreen.getWorld().getBombList().remove(found);
//        }
    }

    private void handleDropBombCommand(DropBombCommand command) {
        if (command.getFromUser().equals(UserSession.getInstance().getUsername())) {
            return;
        }

        gameScreen.onOpponentDropBomb(command.getId(), command.getGridX(), command.getGridY(), command.getFromUser());
    }

    private void handleGameEndCommand(GameEndCommand command) {
        networkInterface.leaveRoom(room.getId());

        if (command.getReason() == GameEndCommand.GameEndReason.OWNER_LEFT) {
            game.getClient().deleteRoom(room.getId());
            Gdx.app.postRunnable(new Runnable() {
                @Override
                public void run() {
                    gameScreen.onOwnerLeft();
                }
            });
        }
        else {
            gameScreen.onGameFinished();
        }
    }

    private void handleMoveGhostCommand(MoveGhostCommand command) {
        gameScreen.onMoveGhost(command.getId(), command.getGridX(), command.getGridY(), command.getDirection(), command.getDistance());
    }

    private void handleCreateGameCommand(CreateGameCommand command) {
        if (UserSession.getInstance().getUsername().equals(command.getFromUser())) {
            return;
        }

        gameScreen.onCreateGame(command.getLabyrinthModel(), command.getGhostModels());
    }

    private void handleMoveStartCommand(MoveCommand command) {
        if (UserSession.getInstance().getUsername().equals(command.getFromUser())) {
            return;
        }
        gameScreen.onMoveStart(command.getFromUser(), GameScreen.Direction.valueOf(command.getDirection()));
    }

    private void handleMoveEndCommand(MoveEndCommand command) {
        if (UserSession.getInstance().getUsername().equals(command.getFromUser())) {
            return;
        }
        gameScreen.getWorld().setPlayerTargetPosition(command.getFromUser(), command.getGridX(), command.getGridY());
        gameScreen.onMoveEnd(command.getFromUser(), GameScreen.Direction.valueOf(command.getDirection()));
    }

    public void setGameServer(GameServer gameServer) {
        this.gameServer = gameServer;
    }

    @Override
    public BomberManScreen createScreen() {
        this.screen = new GameScreen(this.game, this);
        this.gameScreen = (GameScreen) screen;
        return screen;
    }

    public void move(GameScreen.Direction direction) {
        MoveCommand moveCommand = new MoveCommand();
        moveCommand.setDirection(direction.toString());
        moveCommand.setFromUser(UserSession.getInstance().getUsername());
        Vector2 positionGrid = gameScreen.getWorld().getPlayerGridPosition(UserSession.getInstance().getUsername());
        moveCommand.setGridX((int)positionGrid.x);
        moveCommand.setGridY((int)positionGrid.y);
        networkInterface.sendMessage(moveCommand.serialize());
    }

    public void moveEnd(GameScreen.Direction direction) {
        MoveEndCommand moveEndCommand = new MoveEndCommand();
        moveEndCommand.setDirection(direction.toString());
        moveEndCommand.setFromUser(UserSession.getInstance().getUsername());
        Vector2 targetPosition = gameScreen.getWorld().getTargetGridPosition(UserSession.getInstance().getUsername());
        moveEndCommand.setGridX((int)targetPosition.x);
        moveEndCommand.setGridY((int)targetPosition.y);
        networkInterface.sendMessage(moveEndCommand.serialize());
    }

    public void onBombButtonClicked() {
        //TODO check if player can drop bomb.
        BombModel bombModel = gameScreen.getWorld().playerDroppedBomb(UserSession.getInstance().getUsername());
        bombModel.addBombListener(new BombModel.BombListener() {
            @Override
            public void onBombExploded(BombModel bombModel) {
                if (UserSession.getInstance().isServer()) {
                    gameServer.sendBombExplosion(bombModel);
                }
                gameScreen.renderBombExplosion(bombModel);
            }
        });
        DropBombCommand dropBombCommand = new DropBombCommand();
        dropBombCommand.setFromUser(UserSession.getInstance().getUsername());
        dropBombCommand.setId(bombModel.getId());
        dropBombCommand.setGridX(bombModel.getGridX());
        dropBombCommand.setGridY(bombModel.getGridY());
        game.getClient().sendMessage(dropBombCommand.serialize());
        gameScreen.addBombToScreen(bombModel);
    }

    public void onPlayerLeftRoom(String playerName) {
        if (playerName != null && playerName.equals(room.getOwner())) {
            onGameOwnerLeft();
        }
        else {
            // TODO : remove player character from stage
        }
    }

    public void onPlayerJoinedRoom(String playerName) {
        gameScreen.onPlayerJoinedRoom(playerName);
    }

    private void onGameOwnerLeft() {
        GameEndCommand gameEndCommand = new GameEndCommand();
        gameEndCommand.setFromUser(UserSession.getInstance().getUsername());
        gameEndCommand.setReason(GameEndCommand.GameEndReason.OWNER_LEFT);
        networkInterface.sendMessage(gameEndCommand.serialize());
    }

    public RoomModel getRoom() {
        return room;
    }

    public void setRoom(RoomModel room) {
        this.room = room;
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
    }

}
