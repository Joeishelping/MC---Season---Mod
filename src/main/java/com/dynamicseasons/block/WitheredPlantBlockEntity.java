package com.dynamicseasons.block;

import com.dynamicseasons.ModBlocks;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.math.BlockPos;

/** Remembers the exact block state a withered plant used to be. */
public class WitheredPlantBlockEntity extends BlockEntity {
    private BlockState original = Blocks.AIR.getDefaultState();

    public WitheredPlantBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.WITHERED_PLANT_ENTITY, pos, state);
    }

    public BlockState getOriginal() {
        return original;
    }

    public void setOriginal(BlockState original) {
        this.original = original;
        markDirty();
        if (world != null && !world.isClient) {
            // Let clients know too, so pick-block and placement prediction match the server.
            world.updateListeners(pos, getCachedState(), getCachedState(), net.minecraft.block.Block.NOTIFY_LISTENERS);
        }
    }

    @Override
    public Packet<ClientPlayPacketListener> toUpdatePacket() {
        return BlockEntityUpdateS2CPacket.create(this);
    }

    @Override
    public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup registryLookup) {
        return createNbt(registryLookup);
    }

    @Override
    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        super.readNbt(nbt, registryLookup);
        if (nbt.contains("Original")) {
            original = NbtHelper.toBlockState(registryLookup.getWrapperOrThrow(RegistryKeys.BLOCK), nbt.getCompound("Original"));
        }
    }

    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        super.writeNbt(nbt, registryLookup);
        nbt.put("Original", NbtHelper.fromBlockState(original));
    }
}
