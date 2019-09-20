package com.github.unchama.seichiassist.commands

import com.github.unchama.contextualexecutor.builder.{ContextualExecutorBuilder, Parsers}
import com.github.unchama.contextualexecutor.executors.BranchedExecutor
import com.github.unchama.seichiassist.commands.contextual.builder.BuilderTemplates.playerCommandBuilder
import com.github.unchama.seichiassist.listener.MebiusListener
import com.github.unchama.targetedeffect.EmptyEffect

object MebiusCommand {
  private object Messages {
    val commandDescription = List(
        s"${ChatColor.RED}[Usage]",
        s"${ChatColor.RED}/mebius naming [name]",
        s"${ChatColor.RED}  現在頭に装着中のMEBIUSに[name]を命名します。",
        "",
        s"${ChatColor.RED}/mebius nickname",
        s"${ChatColor.RED}  MEBIUSから呼ばれる名前を表示します",
        "",
        s"${ChatColor.RED}/mebius nickname set [name]",
        s"${ChatColor.RED}  MEBIUSから呼ばれる名前を[name]に変更します",
        "",
        s"${ChatColor.RED}/mebius nickname reset",
        s"${ChatColor.RED}  MEBIUSからの呼び名をプレイヤー名(初期設定)に戻します",
        ""
    ).asMessageEffect()

    val permissionWarning = s"${ChatColor.RED}このコマンドは権限者のみが実行可能です.".asMessageEffect()
  }

  private object ChildExecutors {
    val printDescriptionExecutor = ContextualExecutorBuilder.beginConfiguration()
        .execution { Messages.commandDescription }
        .build()

    val getExecutor = playerCommandBuilder
        .execution { context =>
          if (!context.sender.isOp) Messages.permissionWarning else {
            MebiusListener.debugGive(context.sender)
            EmptyEffect
          }
        }
        .build()

    val reloadExecutor = playerCommandBuilder
        .execution { context =>
          if (!context.sender.isOp) Messages.permissionWarning else {
            MebiusListener.reload()
            EmptyEffect
          }
        }
        .build()

    val debugExecutor = playerCommandBuilder
        .execution { context =>
          if (!context.sender.isOp) Messages.permissionWarning else {
            MebiusListener.debug(context.sender)
            EmptyEffect
          }
        }
        .build()

    object NickNameCommand {
      private val checkNickNameExecutor = playerCommandBuilder
          .execution { context =>
            val message = MebiusListener.getNickname(context.sender)
                ?.let { s"${ChatColor.GREEN}現在のメビウスからの呼び名 : $it" }
                ?: s"${ChatColor.RED}呼び名の確認はMEBIUSを装着して行ってください."

            message.asMessageEffect()
          }
          .build()

      private val resetNickNameExecutor = playerCommandBuilder
          .execution { context =>
            val message = if (MebiusListener.setNickname(context.sender, context.sender.name)) {
              s"${ChatColor.GREEN}メビウスからの呼び名を${context.sender.name}にリセットしました."
            } else {
              s"${ChatColor.RED}呼び名のリセットはMEBIUSを装着して行ってください."
            }

            message.asMessageEffect()
          }
          .build()

      private val setNickNameExecutor = playerCommandBuilder
          .argumentsParsers(List(Parsers.identity), onMissingArguments = printDescriptionExecutor)
          .execution { context =>
            val newName = s"${context.args.parsed[0] as String} ${context.args.yetToBeParsed.joinToString(" ")}"
            val message = if (!MebiusListener.setNickname(context.sender, newName)) {
              s"${ChatColor.RED}呼び名の設定はMEBIUSを装着して行ってください."
            } else {
              s"${ChatColor.GREEN}メビウスからの呼び名を${newName}にセットしました."
            }

            message.asMessageEffect()
          }
          .build()

      val executor = BranchedExecutor(mapOf(
          "reset" to resetNickNameExecutor,
          "set" to setNickNameExecutor
      ), whenArgInsufficient = checkNickNameExecutor, whenBranchNotFound = checkNickNameExecutor)
    }

    val namingExecutor = playerCommandBuilder
        .argumentsParsers(List(Parsers.identity))
        .execution { context =>
          val newName = s"${context.args.parsed[0] as String} ${context.args.yetToBeParsed.joinToString(" ")}"

          if (!MebiusListener.setName(context.sender, newName)) {
            s"${ChatColor.RED}命名はMEBIUSを装着して行ってください.".asMessageEffect()
          } else EmptyEffect
        }
        .build()
  }

  val executor = with(ChildExecutors) {
    BranchedExecutor(
        mapOf(
            "get" to getExecutor,
            "reload" to reloadExecutor,
            "debug" to debugExecutor,
            "nickname" to ChildExecutors.NickNameCommand.executor,
            "naming" to namingExecutor
        ), whenArgInsufficient = printDescriptionExecutor, whenBranchNotFound = printDescriptionExecutor
    ).asNonBlockingTabExecutor()
  }
}