package fr.kodistan.interregne.mixin;

import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.minecolonies.core.colony.buildings.modules.AbstractCraftingBuildingModule;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

/**
 * The recipes a player switched off in a crafter: surplus crafting leaves them alone.
 */
@Mixin(value = AbstractCraftingBuildingModule.class, remap = false)
public interface AbstractCraftingBuildingModuleAccessor
{
    @Accessor("disabledRecipes")
    List<IToken<?>> interregne$disabledRecipes();
}
