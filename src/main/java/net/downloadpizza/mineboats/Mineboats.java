package net.downloadpizza.mineboats;

import net.fabricmc.api.ModInitializer;

import static net.fabricmc.fabric.api.client.command.v1.ClientCommandManager.*;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.Closeable;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;

import net.minecraft.server.network.ServerPlayNetworkHandler;

public class Mineboats implements ModInitializer {

    private SplitState state = SplitState.Inactive;
//    private PlayerEntity trackedPlayer;

    private final StartBlock splitLine = new StartBlock();

    private Splitter splitter;

    private Vec3d lastPos;

    private boolean startOnSplit = false;

    protected static Logger LOGGER = LogManager.getFormatterLogger("Fabric|Mineboats");

    private PlayerEntity trackedPlayer;

    @Override
    public void onInitialize() {

        DISPATCHER.register(
                literal("line").then(literal("start")
                        .executes(ctx -> {
                            BlockPos position = ctx.getSource().getPlayer().getBlockPos();
                            splitLine.setPos1(position);
                            ctx.getSource().getPlayer().sendMessage(Text.of("Set start of line to " + position), false);
                            return 1;
                        })).then(literal("end")
                        .executes(ctx -> {
                            BlockPos position = ctx.getSource().getPlayer().getBlockPos();
                            splitLine.setPos2(position);
                            ctx.getSource().getPlayer().sendMessage(Text.of("Set end of line to " + position), false);
                            return 1;
                        }))
        );

        DISPATCHER.register(
                literal("connect")
                        .executes(ctx -> {
                            try {
                                splitter = new Splitter();
                                ctx.getSource().getPlayer().sendMessage(Text.of("Connected to socket"), false);
                            } catch (IOException e) {
                                ctx.getSource().getPlayer().sendMessage(Text.of(e.getMessage()), false);
                            }
                            return 1;
                        })
        );

        DISPATCHER.register(
                literal("splits").then(
                        literal("start")
                                .executes(ctx -> {
                                    state = SplitState.Starting;
                                    startOnSplit = false;
                                    ctx.getSource().getPlayer().sendMessage(Text.of("Starting splits on movement"), false);
                                    return 1;
                                })).then(
                        literal("startonsplit")
                                .executes(ctx -> {
                                    state = SplitState.Active;
                                    startOnSplit = true;
                                    ctx.getSource().getPlayer().sendMessage(Text.of("Starting splits on first line crossing"), false);
                                    return 1;
                                })).then(
                        literal("stop")
                                .executes(ctx -> {
                                    state = SplitState.Inactive;
                                    ctx.getSource().getPlayer().sendMessage(Text.of("Stopped splits"), false);
                                    return 1;
                                }))
        );

//        DISPATCHER.register(
//                literal("track").then(argument("player", StringArgumentType.word())
//                        .executes(ctx -> {
//                            String plr = ctx.getArgument("player", String.class);
//                            trackedPlayer = ctx.getSource().getWorld().getPlayers().stream().filter(p -> p.getGameProfile().getName().equals(plr)).findFirst().orElse(null);
//                            return 1;
//                        }))
//        );
//
//        DISPATCHER.register(
//                literal("track").executes(ctx -> {
//                    trackedPlayer = ctx.getSource().getPlayer();
//                    return 1;
//                })
//        );

        DISPATCHER.register(
                literal("mbdebug").executes(ctx -> {
                    PlayerEntity pe = ctx.getSource().getPlayer();
                    pe.sendMessage(Text.of("Player: " + ctx.getSource().getPlayer()), false);
                    pe.sendMessage(Text.of("State: " + state), false);
                    return 1;
                })
        );

        MinecraftClient instance = MinecraftClient.getInstance();

        ClientTickEvents.END_WORLD_TICK.register(wd -> {
            trackedPlayer = instance.player;
            if (state == SplitState.Inactive || trackedPlayer == null || splitLine.incomplete() || splitter == null)
                return;

            int x = (int) trackedPlayer.getX();
            int y = (int) trackedPlayer.getY();
            int z = (int) trackedPlayer.getZ();

            switch (state) {
                case Starting:
                    if(lastPos == null) {
                        lastPos = trackedPlayer.getPos();
                    } else {
                        if(!lastPos.equals(trackedPlayer.getPos())) {
                            lastPos = null;
                            splitter.startTimer();
                            state = SplitState.FirstLap;
                        }
                    }
                    break;
                case FirstLap:
                    if (splitLine.check(x, y, z)) {
                        state = SplitState.Splitting;
                    }
                    break;
                case Splitting:
                    if (!splitLine.check(x, y, z)) {
                        state = SplitState.Active;
                    }
                    break;
                case Active:
                    if (splitLine.check(x, y, z)) {
                        if (startOnSplit) {
                            splitter.startTimer();
                            startOnSplit = false;
                        } else {
                            splitter.split();
                        }
                        state = SplitState.Splitting;
                    }
                    break;
            }
        });
    }
}

enum SplitState {
    Inactive,
    Starting,
    FirstLap,
    Splitting,
    Active
}

class StartBlock {
    private BlockPos pos1;
    private BlockPos pos2;

    public void setPos1(BlockPos pos1) {
        this.pos1 = pos1;
    }

    public void setPos2(BlockPos pos2) {
        this.pos2 = pos2;
    }

    public boolean incomplete() {
        return pos1 == null || pos2 == null;
    }

    public boolean check(int x, int y, int z) {
        if (incomplete())
            throw new IllegalStateException("Cant run check on incomplete block");

        boolean xCheck = x >= Math.min(pos1.getX(), pos2.getX()) && x <= Math.max(pos1.getX(), pos2.getX());
        boolean yCheck = y >= (Math.min(pos1.getY(), pos2.getY()) - 2) && y <= (Math.max(pos1.getY(), pos2.getY()) + 2);
        boolean zCheck = z >= Math.min(pos1.getZ(), pos2.getZ()) && z <= Math.max(pos1.getZ(), pos2.getZ());

        return xCheck && yCheck && zCheck;
    }
}

class Splitter implements Closeable {
    private final Socket socket;
    private final PrintWriter writer;

    Splitter() throws IOException {
        socket = new Socket("localhost", 16834);
        writer = new PrintWriter(socket.getOutputStream());
    }

    public void startTimer() {
        writer.write("starttimer\r\n");
        writer.flush();
    }

    public void split() {
        writer.write("split\r\n");
        writer.flush();
    }

    @Override
    public void close() throws IOException {
        writer.close();
        socket.close();
    }
}