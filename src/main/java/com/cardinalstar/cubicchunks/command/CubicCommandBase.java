package com.cardinalstar.cubicchunks.command;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.command.CommandBase;

public abstract class CubicCommandBase extends CommandBase {

    private final PermissionLevel requiredPermissionLevel;

    public CubicCommandBase(PermissionLevel permissionLevelRequired) {
        this.requiredPermissionLevel = permissionLevelRequired;
    }

    @Override
    public int getRequiredPermissionLevel() {
        return this.requiredPermissionLevel.ordinal();
    }

    public PermissionLevel getRequiredPermissionEnum() {
        return this.requiredPermissionLevel;
    }

    public static enum PermissionLevel {
        ALL,
        OP,
        NONE
    }

    @Override
    public List<String> getCommandAliases() {
        return new ArrayList<>();
    }
}
