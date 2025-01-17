package com.github.unchama.seichiassist.listener;

import com.github.unchama.seichiassist.*;
import com.github.unchama.seichiassist.data.BreakArea;
import com.github.unchama.seichiassist.data.Coordinate;
import com.github.unchama.seichiassist.data.Mana;
import com.github.unchama.seichiassist.data.player.PlayerData;
import com.github.unchama.seichiassist.task.CoolDownTask;
import com.github.unchama.seichiassist.task.MultiBreakTask;
import com.github.unchama.seichiassist.util.BreakUtil;
import com.github.unchama.seichiassist.util.Util;
import com.github.unchama.seichiassist.util.external.ExternalPlugins;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.*;

public class PlayerBlockBreakListener implements Listener {
	HashMap<UUID,PlayerData> playermap = SeichiAssist.Companion.getPlayermap();
	private SeichiAssist plugin = SeichiAssist.Companion.getInstance();

	//アクティブスキルの実行
	@EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
	public void onPlayerActiveSkillEvent(BlockBreakEvent event){
		//実行したプレイヤーを取得
		Player player = event.getPlayer();

		//デバッグ用
		if(SeichiAssist.Companion.getDEBUG())player.sendMessage("ブロックブレイクイベントが呼び出されました");

		//壊されるブロックを取得
		Block block = event.getBlock();

		//他人の保護がかかっている場合は処理を終了
		if(!ExternalPlugins.getWorldGuard().canBuild(player, block.getLocation()))return;

		//ブロックのタイプを取得
		Material material = block.getType();

		//UUIDを取得
		UUID uuid = player.getUniqueId();
		//UUIDを基にプレイヤーデータ取得
		PlayerData playerdata = SeichiAssist.Companion.getPlayermap().get(uuid);
		//エラー分岐
		if(playerdata == null){
			return;
		}

		//重力値によるキャンセル判定(スキル判定より先に判定させること)
		if(!MaterialSets.INSTANCE.getGravityMaterials().contains(block.getType()) &&
		!MaterialSets.INSTANCE.getCancelledMaterials().contains(block.getType())){
			if(BreakUtil.INSTANCE.getGravity(player, block, false) > 15){
				player.sendMessage(ChatColor.RED + "整地ワールドでは必ず上から掘ってください。");
				event.setCancelled(true);
				return;
			}
		}

		// 保護と重力値に問題無く、ブロックタイプがmateriallistに登録されていたらMebiusListenerを呼び出す
		if(MaterialSets.INSTANCE.getMaterials().contains(material)){
			MebiusListener.onBlockBreak(event);
		}

		//スキル発動条件がそろってなければ終了
		if(!Util.INSTANCE.isSkillEnable(player)){
			return;
		}

		//プレイヤーインベントリを取得
		PlayerInventory inventory = player.getInventory();
		//メインハンドとオフハンドを取得
		ItemStack mainhanditem = inventory.getItemInMainHand();
		ItemStack offhanditem = inventory.getItemInOffHand();
		//実際に使用するツールを格納する
		ItemStack tool;
		//メインハンドにツールがあるか
		boolean mainhandtoolflag = MaterialSets.INSTANCE.getBreakMaterials().contains(mainhanditem.getType());
		//オフハンドにツールがあるか
		boolean offhandtoolflag = MaterialSets.INSTANCE.getBreakMaterials().contains(offhanditem.getType());


		//スキル発動条件がそろってなければ終了
		if(!Util.INSTANCE.isSkillEnable(player)){
			return;
		}

		//場合分け
		if(mainhandtoolflag){
			//メインハンドの時
			tool = mainhanditem;
		}else if(offhandtoolflag){
			//サブハンドの時
			return;
		}else{
			//どちらにももっていない時処理を終了
			return;
		}

		//耐久値がマイナスかつ耐久無限ツールでない時処理を終了
		if(tool.getDurability() > tool.getType().getMaxDurability() && !tool.getItemMeta().spigot().isUnbreakable()){
			return;
		}


		//スキルで破壊されるブロックの時処理を終了
		if(SeichiAssist.Companion.getAllblocklist().contains(block)){
			event.setCancelled(true);
			if(SeichiAssist.Companion.getDEBUG()){
				player.sendMessage("スキルで使用中のブロックです。");
			}
			return;
		}

		//ブロックタイプがmateriallistに登録されていなければ処理終了
		if(!MaterialSets.INSTANCE.getMaterials().contains(material)){
			if(SeichiAssist.Companion.getDEBUG()) player.sendMessage(ChatColor.RED + "破壊対象でない");
			return;
		}

		//もしサバイバルでなければ処理を終了
		//もしフライ中なら終了
		if(player.getGameMode() != GameMode.SURVIVAL || player.isFlying()){
			if(SeichiAssist.Companion.getDEBUG()) player.sendMessage(ChatColor.RED + "fly中の破壊");
			return;
		}

		//クールダウンタイム中は処理を終了
		if(!playerdata.getActiveskilldata().skillcanbreakflag){
			//SEを再生
			if(SeichiAssist.Companion.getDEBUG()) player.sendMessage(ChatColor.RED + "クールタイムの破壊");
			player.playSound(player.getLocation(), Sound.BLOCK_DISPENSER_FAIL, 0.5f, 1);
			return;
		}

		//これ以前の終了処理はマナは回復しません
		//追加マナ獲得
		playerdata.getActiveskilldata().mana.increase(BreakUtil.INSTANCE.calcManaDrop(playerdata),player, playerdata.getLevel());
		//これ以降の終了処理はマナが回復します

		//アクティブスキルフラグがオフの時処理を終了
		if(playerdata.getActiveskilldata().mineflagnum == 0 || playerdata.getActiveskilldata().skillnum == 0 || playerdata.getActiveskilldata().skilltype == 0 || playerdata.getActiveskilldata().skilltype == ActiveSkill.ARROW.gettypenum()){
			if(SeichiAssist.Companion.getDEBUG()) player.sendMessage(ChatColor.RED + "スキルオフ時の破壊");
			return;
		}


		if(playerdata.getActiveskilldata().skilltype == ActiveSkill.MULTI.gettypenum()){
			runMultiSkill(player, block, tool);
		}else if(playerdata.getActiveskilldata().skilltype == ActiveSkill.BREAK.gettypenum()){
			runBreakSkill(player, block, tool);
		}
	}
	//複数範囲破壊
	private void runMultiSkill(Player player, Block block,
			ItemStack tool) {


		//UUIDを取得
		UUID uuid = player.getUniqueId();
		//playerdataを取得
		PlayerData playerdata = playermap.get(uuid);
		//レベルを取得
		//int skilllevel = playerdata.activeskilldata.skillnum;
		//マナを取得
		Mana mana = playerdata.getActiveskilldata().mana;
		//プレイヤーの足のy座標を取得
		int playerlocy = player.getLocation().getBlockY() - 1 ;
		//元ブロックのマテリアルを取得
		Material material = block.getType();
		//元ブロックの真ん中の位置を取得
		Location centerofblock = block.getLocation().add(0.5, 0.5, 0.5);



		//壊されるブロックの宣言
		Block breakblock;
		//実際に破壊するブロック数
		long breakblocknum = 0;
		final BreakArea area = playerdata.getActiveskilldata().area;
		//現在のプレイヤーの向いている方向
		String dir = BreakUtil.INSTANCE.getCardinalDirection(player);
		//もし前回とプレイヤーの向いている方向が違ったら範囲を取り直す
		if(!dir.equals(area.getDir())){
			area.setDir(dir);
			area.makeArea();
		}

		final List<Coordinate> startlist = area.getStartList();
		final List<Coordinate> endlist = area.getEndList();

		//エフェクト用に壊されるブロック全てのリストデータ
		List<List<Block>> multibreaklist = new ArrayList<>();

		//壊される溶岩の全てのリストデータ
		List<List<Block>> multilavalist = new ArrayList<>();

		//エフェクト用に壊されるブロック全てのリストデータ
		List<Block> breaklist = new ArrayList<>();

		//壊される溶岩のリストデータ
		List<Block> lavalist = new ArrayList<>();

		//繰り返す回数
		final int breaknum = area.getBreakNum();
		//一回の破壊の範囲
		final Coordinate breaklength = area.getBreakLength();
		//１回の全て破壊したときのブロック数
		final int ifallbreaknum = (breaklength.x * breaklength.y * breaklength.z * breaknum);

		//全てのマナ消費量
		double useAllMana = 0;
		//全ての耐久消費量
		short alldurability =  tool.getDurability();
		//繰り返し回数だけ繰り返す
		for(int i = 0; i < breaknum ; i++){
			breaklist.clear();
			lavalist.clear();
			Coordinate start = startlist.get(i);
			Coordinate end = endlist.get(i);
			//for(int y = start.y; y <= end.y ; y++){
			for(int y = end.y; y >= start.y ; y--){ //上から処理に変更
				for(int x = start.x ; x <= end.x ; x++){
					for(int z = start.z ; z <= end.z ; z++){
						breakblock = block.getRelative(x, y, z);
						if(x == 0 && y == 0 && z == 0)continue;

						if(playerdata.getLevel() >= SeichiAssist.Companion.getSeichiAssistConfig().getMultipleIDBlockBreaklevel() && playerdata.getSettings().getMultipleidbreakflag()) { //追加テスト(複数種類一括破壊スキル)
							if(breakblock.getType() != Material.AIR && breakblock.getType() != Material.BEDROCK) {
								if(breakblock.getType() == Material.STATIONARY_LAVA || BreakUtil.INSTANCE.BlockEqualsMaterialList(breakblock)){
									if(playerlocy < breakblock.getLocation().getBlockY() || player.isSneaking() || breakblock.equals(block)){
										if(BreakUtil.INSTANCE.canBreak(player, breakblock)){
											if(breakblock.getType() == Material.STATIONARY_LAVA){
												lavalist.add(breakblock);
											}else{
												breaklist.add(breakblock);
												SeichiAssist.Companion.getAllblocklist().add(breakblock);
											}
										}
									}
								}
							}
						} else { //条件を満たしていない
							//もし壊されるブロックがもともとのブロックと同じ種類だった場合
							if(breakblock.getType() == material
									|| (block.getType() == Material.DIRT && breakblock.getType() == Material.GRASS)
									|| (block.getType() == Material.GRASS && breakblock.getType() == Material.DIRT)
									|| (block.getType() == Material.GLOWING_REDSTONE_ORE && breakblock.getType() == Material.REDSTONE_ORE)
									|| (block.getType() == Material.REDSTONE_ORE && breakblock.getType() == Material.GLOWING_REDSTONE_ORE)
									|| breakblock.getType() == Material.STATIONARY_LAVA
									){
								if(playerlocy < breakblock.getLocation().getBlockY() || player.isSneaking() || breakblock.equals(block)){
									if(BreakUtil.INSTANCE.canBreak(player, breakblock)){
										if(breakblock.getType() == Material.STATIONARY_LAVA){
											lavalist.add(breakblock);
										}else{
											breaklist.add(breakblock);
											SeichiAssist.Companion.getAllblocklist().add(breakblock);
										}
									}
								}
							}
						}

					}
				}
			}

			//重力値計算
			int gravity = BreakUtil.INSTANCE.getGravity(player,block,false);


			//減る経験値計算
			//実際に破壊するブロック数  * 全てのブロックを破壊したときの消費経験値÷すべての破壊するブロック数 * 重力

			useAllMana += (double) (breaklist.size() + 1) * (gravity + 1)
					* ActiveSkill.getActiveSkillUseExp(playerdata.getActiveskilldata().skilltype, playerdata.getActiveskilldata().skillnum)
					/(ifallbreaknum * breaknum) ;


			//減る耐久値の計算
			alldurability += BreakUtil.INSTANCE.calcDurability(tool.getEnchantmentLevel(Enchantment.DURABILITY),breaklist.size());
			//１マス溶岩を破壊するのにはブロック１０個分の耐久が必要
			alldurability += BreakUtil.INSTANCE.calcDurability(tool.getEnchantmentLevel(Enchantment.DURABILITY),10*lavalist.size());

			//重力値の判定
			if(gravity > 15){
				player.sendMessage(ChatColor.RED + "スキルを使用するには上から掘ってください。");
				SeichiAssist.Companion.getAllblocklist().removeAll(breaklist);
				break;
			}
			//実際に経験値を減らせるか判定
			if(!mana.has(useAllMana)){
				//デバッグ用
				if(SeichiAssist.Companion.getDEBUG()){
					player.sendMessage(ChatColor.RED + "アクティブスキル発動に必要なマナが足りません");
				}

				SeichiAssist.Companion.getAllblocklist().removeAll(breaklist);

				break;
			}
			//実際に耐久値を減らせるか判定
			if(tool.getType().getMaxDurability() <= alldurability && !tool.getItemMeta().spigot().isUnbreakable()){
				//デバッグ用
				if(SeichiAssist.Companion.getDEBUG()){
					player.sendMessage(ChatColor.RED + "アクティブスキル発動に必要なツールの耐久値が足りません");
				}
				SeichiAssist.Companion.getAllblocklist().removeAll(breaklist);

				break;
			}

			//選択されたブロックを破壊せずに保存する処理
			multibreaklist.add(new ArrayList<>(breaklist));
			multilavalist.add(new ArrayList<>(lavalist));
			breakblocknum += breaklist.size();
		}




		//自身のみしか壊さない時自然に処理する
		if(breakblocknum==0){
			BreakUtil.INSTANCE.breakBlock(player, block, centerofblock, tool,true);
			return;
		}//スキルの処理
		else{
			multibreaklist.get(0).add(block);
			SeichiAssist.Companion.getAllblocklist().add(block);
			new MultiBreakTask(player,block,tool,multibreaklist,multilavalist,startlist,endlist).runTaskTimer(plugin,0,4);
		}


		//経験値を減らす
		mana.decrease(useAllMana,player, playerdata.getLevel());

		//耐久値を減らす
		if(!tool.getItemMeta().spigot().isUnbreakable()){
			tool.setDurability(alldurability);
		}

		//壊したブロック数に応じてクールダウンを発生させる
		long cooldown = ActiveSkill.MULTI.getCoolDown(playerdata.getActiveskilldata().skillnum) * breakblocknum /(ifallbreaknum);
		if(cooldown >= 5){
			new CoolDownTask(player,false,true,false).runTaskLater(plugin,cooldown);
		}
	}

	//範囲破壊実行処理
	private void runBreakSkill(Player player,Block block,ItemStack tool) {
		//UUIDを取得
		UUID uuid = player.getUniqueId();
		//playerdataを取得
		PlayerData playerdata = playermap.get(uuid);
		//レベルを取得
		//int skilllevel = playerdata.activeskilldata.skillnum;
		//マナを取得
		Mana mana = playerdata.getActiveskilldata().mana;
		//プレイヤーの足のy座標を取得
		int playerlocy = player.getLocation().getBlockY() - 1 ;
		//元ブロックのマテリアルを取得
		Material material = block.getType();
		//元ブロックの真ん中の位置を取得
		Location centerofblock = block.getLocation().add(0.5, 0.5, 0.5);

		//壊されるブロックの宣言
		Block breakblock;
		//壊される範囲を設定
		BreakArea area = playerdata.getActiveskilldata().area;
		//現在のプレイヤーの向いている方向
		String dir = BreakUtil.INSTANCE.getCardinalDirection(player);
		//もし前回とプレイヤーの向いている方向が違ったら範囲を取り直す
		if(!dir.equals(area.getDir())){
			area.setDir(dir);
			area.makeArea();
		}
		Coordinate start = area.getStartList().get(0);
		Coordinate end = area.getEndList().get(0);
		//エフェクト用に壊されるブロック全てのリストデータ
		Set<Block> breaklist = new HashSet<>();

		//壊される溶岩のリストデータ
		Set<Block> lavalist = new HashSet<>();

		//範囲内の破壊されるブロックを取得
		//for(int y = start.y; y <= end.y ; y++){
		for(int y = end.y; y >= start.y ; y--){ //上から処理に変更
			for(int x = start.x ; x <= end.x ; x++){
				for(int z = start.z ; z <= end.z ; z++){
					breakblock = block.getRelative(x, y, z);
					if(x == 0 && y == 0 && z == 0)continue;

					if(playerdata.getLevel() >= SeichiAssist.Companion.getSeichiAssistConfig().getMultipleIDBlockBreaklevel() && (Util.INSTANCE.isSeichiWorld(player) || playerdata.getSettings().getMultipleidbreakflag())) { //追加テスト(複数種類一括破壊スキル)
						if(breakblock.getType() != Material.AIR && breakblock.getType() != Material.BEDROCK) {
							if(breakblock.getType() == Material.STATIONARY_LAVA || BreakUtil.INSTANCE.BlockEqualsMaterialList(breakblock)){
								if(playerlocy < breakblock.getLocation().getBlockY() || player.isSneaking() || breakblock.equals(block)){
									if(BreakUtil.INSTANCE.canBreak(player, breakblock)){
										if(breakblock.getType() == Material.STATIONARY_LAVA){
											lavalist.add(breakblock);
										}else{
											breaklist.add(breakblock);
											SeichiAssist.Companion.getAllblocklist().add(breakblock);
										}
									}
								}
							}
						}
					} else { //条件を満たしていない
						//もし壊されるブロックがもともとのブロックと同じ種類だった場合
						if(breakblock.getType() == material
								|| (block.getType() == Material.DIRT && breakblock.getType() == Material.GRASS)
								|| (block.getType() == Material.GRASS && breakblock.getType() == Material.DIRT)
								|| (block.getType() == Material.GLOWING_REDSTONE_ORE && breakblock.getType() == Material.REDSTONE_ORE)
								|| (block.getType() == Material.REDSTONE_ORE && breakblock.getType() == Material.GLOWING_REDSTONE_ORE)
								|| breakblock.getType() == Material.STATIONARY_LAVA
								){
							if(playerlocy < breakblock.getLocation().getBlockY() || player.isSneaking() || breakblock.equals(block)){
								if(BreakUtil.INSTANCE.canBreak(player, breakblock)){
									if(breakblock.getType() == Material.STATIONARY_LAVA){
										lavalist.add(breakblock);
									}else{
										breaklist.add(breakblock);
										SeichiAssist.Companion.getAllblocklist().add(breakblock);
									}
								}
							}
						}
					}
				}
			}
		}




		//重力値計算
		int gravity = BreakUtil.INSTANCE.getGravity(player,block,false);


		//減るマナ計算
		//実際に破壊するブロック数  * 全てのブロックを破壊したときの消費経験値÷すべての破壊するブロック数 * 重力
		Coordinate breaklength = area.getBreakLength();
		int ifallbreaknum = (breaklength.x * breaklength.y * breaklength.z);
		double useMana = (double) (breaklist.size()+1) * (gravity + 1)
				* ActiveSkill.getActiveSkillUseExp(playerdata.getActiveskilldata().skilltype, playerdata.getActiveskilldata().skillnum)
				/ifallbreaknum ;
		if(SeichiAssist.Companion.getDEBUG()){
			player.sendMessage(ChatColor.RED + "必要経験値：" + ActiveSkill.getActiveSkillUseExp(playerdata.getActiveskilldata().skilltype, playerdata.getActiveskilldata().skillnum));
			player.sendMessage(ChatColor.RED + "全ての破壊数：" + ifallbreaknum);
			player.sendMessage(ChatColor.RED + "実際の破壊数：" + breaklist.size());
			player.sendMessage(ChatColor.RED + "アクティブスキル発動に必要なマナ：" + useMana);
		}
		//減る耐久値の計算
		short durability = (short) (tool.getDurability() + BreakUtil.INSTANCE.calcDurability(tool.getEnchantmentLevel(Enchantment.DURABILITY),breaklist.size()));
		//１マス溶岩を破壊するのにはブロック１０個分の耐久が必要
		durability += BreakUtil.INSTANCE.calcDurability(tool.getEnchantmentLevel(Enchantment.DURABILITY),10 * lavalist.size());


		//重力値の判定
		if(gravity > 15){
			player.sendMessage(ChatColor.RED + "スキルを使用するには上から掘ってください。");
			SeichiAssist.Companion.getAllblocklist().removeAll(breaklist);
			return;
		}


		//実際に経験値を減らせるか判定
		if(!mana.has(useMana)){
			//デバッグ用
			if(SeichiAssist.Companion.getDEBUG()){
				player.sendMessage(ChatColor.RED + "アクティブスキル発動に必要なマナが足りません");
			}
			SeichiAssist.Companion.getAllblocklist().removeAll(breaklist);
			return;
		}
		if(SeichiAssist.Companion.getDEBUG()){
			player.sendMessage(ChatColor.RED + "アクティブスキル発動後のツールの耐久値:" + durability);
		}

		//実際に耐久値を減らせるか判定
		if(tool.getType().getMaxDurability() <= durability && !tool.getItemMeta().spigot().isUnbreakable()){
			//デバッグ用
			if(SeichiAssist.Companion.getDEBUG()){
				player.sendMessage(ChatColor.RED + "アクティブスキル発動に必要なツールの耐久値が足りません");
			}
			SeichiAssist.Companion.getAllblocklist().removeAll(breaklist);
			return;
		}

		//破壊する処理

		//溶岩の破壊する処理
		for (Block value : lavalist) {
			value.setType(Material.AIR);
		}

		//選択されたブロックを破壊する処理

		//自身のみしか壊さない時自然に処理する
		if(breaklist.isEmpty()){
			BreakUtil.INSTANCE.breakBlock(player, block, centerofblock, tool,true);
			return;
		}//エフェクトが指定されていないときの処理
		else if(playerdata.getActiveskilldata().effectnum == 0){
			breaklist.add(block);
			SeichiAssist.Companion.getAllblocklist().add(block);
			for(Block b:breaklist){
				BreakUtil.INSTANCE.breakBlock(player, b, centerofblock, tool,false);
				SeichiAssist.Companion.getAllblocklist().remove(b);
			}
		}
		//通常エフェクトが指定されているときの処理(100以下の番号に割り振る）
		else if(playerdata.getActiveskilldata().effectnum <= 100){
			breaklist.add(block);
			SeichiAssist.Companion.getAllblocklist().add(block);
			ActiveSkillEffect[] skilleffect = ActiveSkillEffect.values();
			skilleffect[playerdata.getActiveskilldata().effectnum - 1].runBreakEffect(player,playerdata.getActiveskilldata(), tool, breaklist, start, end,centerofblock);
		}

		//スペシャルエフェクトが指定されているときの処理(１０１からの番号に割り振る）
		else if(playerdata.getActiveskilldata().effectnum > 100){
			breaklist.add(block);
			SeichiAssist.Companion.getAllblocklist().add(block);
			ActiveSkillPremiumEffect[] premiumeffect = ActiveSkillPremiumEffect.values();
			premiumeffect[playerdata.getActiveskilldata().effectnum - 1 - 100].runBreakEffect(player, tool, breaklist, start, end,centerofblock);
		}

		//経験値を減らす
		mana.decrease(useMana,player, playerdata.getLevel());

		//耐久値を減らす
		if(!tool.getItemMeta().spigot().isUnbreakable()){
			tool.setDurability(durability);
		}

		//壊したブロック数に応じてクールダウンを発生させる
		long cooldown = ActiveSkill.BREAK.getCoolDown(playerdata.getActiveskilldata().skillnum) * breaklist.size() /ifallbreaknum;
		if(cooldown >= 5){
			new CoolDownTask(player,false,true,false).runTaskLater(plugin,cooldown);
		}
	}

	/**
	 * y5ハーフブロック破壊抑制
	 *
	 * @param event BlockBreakEvent
	 */
	@EventHandler(priority = EventPriority.LOWEST)
	@SuppressWarnings("deprecation")
	public void onPlayerBlockHalf(BlockBreakEvent event) {
		Player p = event.getPlayer();
		Block b = event.getBlock();
		World world = p.getWorld();
		PlayerData data = SeichiAssist.Companion.getPlayermap().get(p.getUniqueId());

		//そもそも自分の保護じゃなきゃ処理かけない
		if(!ExternalPlugins.getWorldGuard().canBuild(p, b.getLocation())) {
			return;
		}

		if (b.getType() == Material.DOUBLE_STEP && b.getData() == 0) {
			b.setType(Material.STEP);
			b.setData((byte) 0);

			Location location = b.getLocation();
			world.dropItemNaturally(location, new ItemStack(Material.STEP));
		}

		if (b.getType() != Material.STEP) {
			return;
		}

		if (b.getY() != 5) {
			return;
		}

		if (b.getData() != 0) {
			return;
		}

		if (!world.getName().toLowerCase().startsWith(SeichiAssist.Companion.getSEICHIWORLDNAME())) {
			return;
		}

		if (data.canBreakHalfBlock()) {
			return;
		}

		event.setCancelled(true);
		p.sendMessage(ChatColor.RED + "Y5に敷かれたハーフブロックは破壊不可能です.");
	}

}
