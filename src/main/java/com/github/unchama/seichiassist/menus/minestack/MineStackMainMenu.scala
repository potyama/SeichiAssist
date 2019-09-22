package com.github.unchama.seichiassist.menus.minestack

import com.github.unchama.menuinventory.slot.button.action
import com.github.unchama.menuinventory.{IndexedSlotLayout, Menu, MenuInventoryView}
import com.github.unchama.seichiassist.menus.CommonButtons
import com.github.unchama.seichiassist.minestack.MineStackObjectCategory
import com.github.unchama.seichiassist.{CommonSoundEffects, Schedulers, SeichiAssist}
import com.github.unchama.targetedeffect
import com.github.unchama.targetedeffect.TargetedEffect
import com.github.unchama.util.kotlin2scala.SuspendingMethod
import org.bukkit.ChatColor._
import org.bukkit.Material
import org.bukkit.entity.Player

object MineStackMainMenu extends Menu {
  val categoryButtonLayout = run {
    def iconMaterialFor(category: MineStackObjectCategory): Material = when (category) {
      ORES => Material.DIAMOND_ORE
        MOB_DROP => Material.ENDER_PEARL
          AGRICULTURAL => Material.SEEDS
            BUILDING => Material.SMOOTH_BRICK
              REDSTONE_AND_TRANSPORTATION => Material.REDSTONE
                GACHA_PRIZES => Material.GOLDEN_APPLE
    }

    val layoutMap = MineStackObjectCategory.values().mapIndexed { index, category =>
      val slotIndex = index + 1 // 0には自動スタック機能トグルが入るので、1から入れ始める
      val iconItemStack = IconItemStackBuilder(iconMaterialFor(category))
        .title(s"$BLUE$UNDERLINE$BOLD${category.uiLabel}")
        .build()

      val button = button.Button(
        iconItemStack,
        action.LeftClickButtonEffect(
          CommonSoundEffects.menuTransitionFenceSound,
          CategorizedMineStackMenu.forCategory(category).open
        )
      )
      slotIndex to button
    }.toMap()

    IndexedSlotLayout(layoutMap)
  }

  private case class ButtonComputations(val player: Player) extends AnyVal {

    /**
     * メインメニュー内の「履歴」機能部分のレイアウトを計算する
     */
    @SuspendingMethod def computeHistoricalMineStackLayout(): IndexedSlotLayout = {
      val playerData = SeichiAssist.playermap(uniqueId)

      val buttonMapping = playerData.hisotryData.usageHistory.mapIndexed { index, mineStackObject =>
        val slotIndex = 18 + index // 3行目から入れだす
        val button = with(MineStackButtons) { getMineStackItemButtonOf(mineStackObject) }

        slotIndex to button
      }.toMap()

      return IndexedSlotLayout(buttonMapping)
    }
  }

  private @SuspendingMethod def computeMineStackMainMenuLayout(player: Player): IndexedSlotLayout = {
    val computations = ButtonComputations(player)
    import computations._

    IndexedSlotLayout(
      0 -> MineStackButtons(player).computeAutoMineStackToggleButton(),
      45 -> CommonButtons.openStickMenu
    )
      .merge(categoryButtonLayout)
      .merge(computeHistoricalMineStackLayout())
  }

  override val open: TargetedEffect[Player] = computedEffect { player =>
    val session = MenuInventoryView(
        6.rows(),
        s"$DARK_PURPLE${BOLD}MineStackメインメニュー"
    ).createNewSession()

    sequentialEffect(
        session.openEffectThrough(Schedulers.sync),
        targetedeffect.UnfocusedEffect { session.overwriteViewWith(player.computeMineStackMainMenuLayout()) }
    )
  }
}
