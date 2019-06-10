package com.builtbroken.durnace.content;

import com.builtbroken.durnace.content.inv.InventoryDuranceBottom;
import com.builtbroken.durnace.content.inv.InventoryDurnace;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.FurnaceRecipes;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityFurnace;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.wrapper.RangedWrapper;

import javax.annotation.Nullable;

public class TileDurnace extends TileEntity implements ITickable
{
    //Inventory
    public final ItemStackHandler inventory = new InventoryDurnace(this);

    //Wrappers
    private final IItemHandler handlerTop = new RangedWrapper(inventory, 0, 1);
    private final IItemHandler handlerBottom = new InventoryDuranceBottom(this, inventory);
    private final IItemHandler handlerSide = new RangedWrapper(inventory, 1, 2);

    /** The number of ticks that the furnace will keep burning */
    public int furnaceBurnTime;
    /** The number of ticks that a fresh copy of the currently-burning item would keep the furnace burning for */
    public int currentItemBurnTime;
    public int cookTime;
    public int totalCookTime;
    private String furnaceCustomName;

    @Override
    public void update()
    {
        boolean isBurning = this.isBurning();
        boolean hasChanged = false;

        if (isBurning())
        {
            --this.furnaceBurnTime;
        }

        if (!this.world.isRemote)
        {
            ItemStack fuelSlotStack = inventory.getStackInSlot(1);

            //Already burning or not burning but has fuel + has input item
            if (this.isBurning() || !fuelSlotStack.isEmpty() && !((ItemStack) inventory.getStackInSlot(0)).isEmpty())
            {
                if (!this.isBurning() && this.canSmelt())
                {
                    this.furnaceBurnTime = TileEntityFurnace.getItemBurnTime(fuelSlotStack);
                    this.currentItemBurnTime = this.furnaceBurnTime;

                    if (this.isBurning())
                    {
                        hasChanged = true;

                        if (!fuelSlotStack.isEmpty())
                        {
                            Item item = fuelSlotStack.getItem();
                            fuelSlotStack.shrink(1);

                            if (fuelSlotStack.isEmpty())
                            {
                                ItemStack item1 = item.getContainerItem(fuelSlotStack);
                                inventory.setStackInSlot(1, item1);
                            }
                        }
                    }
                }

                if (this.isBurning() && this.canSmelt())
                {
                    ++this.cookTime;

                    this.totalCookTime = this.getCookTime(inventory.getStackInSlot(0));
                    if (this.cookTime == this.totalCookTime)
                    {
                        this.cookTime = 0;
                        this.smeltItem();
                        hasChanged = true;
                    }
                }
                else
                {
                    this.cookTime = 0;
                }
            }
            //Slowly remove cook time when we run out of fuel
            else if (!this.isBurning() && this.cookTime > 0)
            {
                this.cookTime = MathHelper.clamp(this.cookTime - 2, 0, this.totalCookTime);
            }

            //Update block state
            if (isBurning != this.isBurning())
            {
                hasChanged = true;
                IBlockState state = world.getBlockState(getPos());
                if (state.getProperties().containsKey(BlockDurnace.BURNING))
                {
                    world.setBlockState(getPos(), state.withProperty(BlockDurnace.BURNING, this.isBurning()));
                }
            }
        }

        //Mark chunk for saving
        if (hasChanged)
        {
            this.markDirty();
        }
    }

    @Override
    public ITextComponent getDisplayName()
    {
        if(hasCustomName())
        {
            return new TextComponentString(furnaceCustomName);
        }
        return new TextComponentTranslation("container.furnace");
    }

    public boolean hasCustomName()
    {
        return this.furnaceCustomName != null && !this.furnaceCustomName.trim().isEmpty();
    }

    public void setCustomInventoryName(String p_145951_1_)
    {
        this.furnaceCustomName = p_145951_1_;

    }

    @Override
    public void readFromNBT(NBTTagCompound compound)
    {
        super.readFromNBT(compound);
        this.furnaceBurnTime = compound.getInteger("BurnTime");
        this.cookTime = compound.getInteger("CookTime");
        this.totalCookTime = compound.getInteger("CookTimeTotal");
        this.currentItemBurnTime = TileEntityFurnace.getItemBurnTime(inventory.getStackInSlot(1));
        inventory.deserializeNBT(compound.getCompoundTag("inventory"));

        if (compound.hasKey("CustomName", 8))
        {
            this.furnaceCustomName = compound.getString("CustomName");
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound)
    {
        super.writeToNBT(compound);
        compound.setInteger("BurnTime", (short) this.furnaceBurnTime);
        compound.setInteger("CookTime", (short) this.cookTime);
        compound.setInteger("CookTimeTotal", (short) this.totalCookTime);
        compound.setTag("inventory", inventory.serializeNBT());

        if (this.hasCustomName())
        {
            compound.setString("CustomName", this.furnaceCustomName);
        }

        return compound;
    }

    /**
     * Furnace isBurning
     */
    public boolean isBurning()
    {
        return this.furnaceBurnTime > 0;
    }

    @SideOnly(Side.CLIENT)
    public static boolean isBurning(IInventory inventory)
    {
        return inventory.getField(0) > 0;
    }

    public int getCookTime(ItemStack stack)
    {
        return 200;
    }

    /**
     * Returns true if the furnace can smelt an item, i.e. has a source item, destination stack isn't full, etc.
     */
    private boolean canSmelt()
    {
        final ItemStack inputSlotStack = inventory.getStackInSlot(0);
        if (inputSlotStack.isEmpty())
        {
            return false;
        }
        else
        {
            ItemStack recipeResult = FurnaceRecipes.instance().getSmeltingResult(inputSlotStack);

            if (recipeResult.isEmpty())
            {
                return false;
            }
            else
            {
                ItemStack outputSlotStack = inventory.getStackInSlot(2);

                if (outputSlotStack.isEmpty())
                {
                    return true;
                }
                else if (!outputSlotStack.isItemEqual(recipeResult))
                {
                    return false;
                }
                else if (outputSlotStack.getCount() + recipeResult.getCount() <= inventory.getSlotLimit(2) && outputSlotStack.getCount() + recipeResult.getCount() <= outputSlotStack.getMaxStackSize())  // Forge fix: make furnace respect stack sizes in furnace recipes
                {
                    return true;
                }
                else
                {
                    return outputSlotStack.getCount() + recipeResult.getCount() <= recipeResult.getMaxStackSize(); // Forge fix: make furnace respect stack sizes in furnace recipes
                }
            }
        }
    }

    /**
     * Turn one item from the furnace source stack into the appropriate smelted item in the furnace result stack
     */
    public void smeltItem()
    {
        if (this.canSmelt())
        {
            ItemStack inputSlotStack = inventory.getStackInSlot(0);
            ItemStack recipeResult = FurnaceRecipes.instance().getSmeltingResult(inputSlotStack);
            ItemStack outputSlotStack = inventory.getStackInSlot(2);

            if (outputSlotStack.isEmpty())
            {
                inventory.setStackInSlot(2, recipeResult.copy());
            }
            else if (outputSlotStack.getItem() == recipeResult.getItem())
            {
                outputSlotStack.grow(recipeResult.getCount());
                inventory.setStackInSlot(2, outputSlotStack);
            }

            if (inputSlotStack.getItem() == Item.getItemFromBlock(Blocks.SPONGE) && inputSlotStack.getMetadata() == 1 && !((ItemStack) inventory.getStackInSlot(1)).isEmpty() && ((ItemStack) inventory.getStackInSlot(1)).getItem() == Items.BUCKET)
            {
                inventory.setStackInSlot(1, new ItemStack(Items.WATER_BUCKET));
            }

            inputSlotStack.shrink(1);
        }
    }


    @Override
    public boolean hasCapability(Capability<?> capability, @Nullable EnumFacing facing)
    {
        return capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY || super.hasCapability(capability, facing);
    }

    @Override
    public <T> T getCapability(Capability<T> capability, @Nullable EnumFacing facing)
    {
        if (facing != null && capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY)
        {
            if (facing == EnumFacing.DOWN)
            {
                return (T) handlerBottom;
            }
            else if (facing == EnumFacing.UP)
            {
                return (T) handlerTop;
            }
            else
            {
                return (T) handlerSide;
            }
        }
        return super.getCapability(capability, facing);
    }

    @Override
    public boolean shouldRefresh(World world, BlockPos pos, IBlockState oldState, IBlockState newState)
    {
        return oldState.getBlock() != newState.getBlock();
    }
}
