package dev.isxander.zoomify.config

import dev.isxander.yacl3.api.*
import dev.isxander.yacl3.api.controller.*
import dev.isxander.yacl3.api.utils.OptionUtils
import dev.isxander.yacl3.config.v3.register
import dev.isxander.yacl3.config.v3.value
import dev.isxander.yacl3.dsl.*
import dev.isxander.zoomify.Zoomify
import dev.isxander.zoomify.config.demo.ControlEmulation
import dev.isxander.zoomify.config.demo.FirstPersonDemo
import dev.isxander.zoomify.config.demo.ThirdPersonDemo
import dev.isxander.zoomify.config.demo.ZoomDemoImageRenderer
import dev.isxander.zoomify.config.migrator.Migrator
import dev.isxander.zoomify.utils.toast
import dev.isxander.zoomify.zoom.*
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.CommonComponents
import net.minecraft.network.chat.Component
import net.minecraft.util.Mth
import kotlin.reflect.KMutableProperty0

fun createSettingsGui(parent: Screen?) = SettingsGuiFactory().createSettingsGui(parent)

private class SettingsGuiFactory {
    var inTransition = ZoomifySettings.zoomInTransition.value
    var outTransition = ZoomifySettings.zoomOutTransition.value
    var inDuration = ZoomifySettings.zoomInTime.value
    var outDuration = ZoomifySettings.zoomOutTime.value
    var initialZoomAmt = ZoomifySettings.initialZoom.value
    var scrollZoomAmt = ZoomifySettings.scrollZoomAmount.value
    var linearLikeSteps = ZoomifySettings.linearLikeSteps.value
    var scrollZoomSmoothness = ZoomifySettings.scrollZoomSmoothness.value
    var canScrollZoom = ZoomifySettings.scrollZoom.value
    var secondaryZoomInTime = ZoomifySettings.secondaryZoomInTime.value
    var secondaryZoomOutTime = ZoomifySettings.secondaryZoomOutTime.value
    var secondaryZoomAmount = ZoomifySettings.secondaryZoomAmount.value

    val zoomHelperFactory = { ZoomHelper(
        TransitionInterpolator(
            { inTransition },
            { outTransition },
            { inDuration },
            { outDuration },
        ),
        SmoothInterpolator {
            Mth.lerp(
                scrollZoomSmoothness / 100.0,
                1.0,
                0.1
            )
        },
        { initialZoomAmt },
        { scrollZoomAmt },
        { if (canScrollZoom) 10 else 0 },
        { linearLikeSteps }
    )}
    val initialOnlyDemo = FirstPersonDemo(zoomHelperFactory(), ControlEmulation.InitialOnly).also {
        it.keepHandFov = !ZoomifySettings.affectHandFov.value
    }
    val scrollOnlyDemo = FirstPersonDemo(zoomHelperFactory(), ControlEmulation.ScrollOnly).also {
        it.keepHandFov = !ZoomifySettings.affectHandFov.value
    }
    val secondaryZoomDemo = ThirdPersonDemo(
        ZoomHelper(
            TimedInterpolator({ secondaryZoomInTime }, { secondaryZoomOutTime }),
            InstantInterpolator,
            initialZoom = { secondaryZoomAmount },
            scrollZoomAmount = { 0 },
            maxScrollTiers = { 0 },
            linearLikeSteps = { false },
        ),
        ControlEmulation.InitialOnly
    ).also {
        it.renderHud = !ZoomifySettings.secondaryHideHUDOnZoom.value
    }

    fun <T> Option.Builder<T>.updateDemo(updateFunc: (T, ZoomDemoImageRenderer) -> Unit) {
        listener { opt, v ->
            updateFunc(v, initialOnlyDemo)
            updateFunc(v, scrollOnlyDemo)
            updateFunc(v, secondaryZoomDemo)
            initialOnlyDemo.pause()
            scrollOnlyDemo.pause()
            secondaryZoomDemo.pause()
        }
    }

    fun <T> Option.Builder<T>.updateDemo(prop: KMutableProperty0<T>) {
        updateDemo { v, _ -> prop.set(v) }
    }

    fun createSettingsGui(parent: Screen?) = YetAnotherConfigLib("zoomify") {
        save(ZoomifySettings::saveToFile)

        val behaviour by categories.registering {
            val basic by groups.registering {
                options.register(ZoomifySettings.initialZoom) {
                    controller = slider(range = 1..10, step = 1, formatter = { v: Int ->
                        Component.literal("%dx".format(v))
                    })
                    demoDefaults(initialOnlyDemo, ::initialZoomAmt)
                }

                options.register(ZoomifySettings.zoomInTime) {
                    controller = slider(range = 0.1..5.0, step = 0.1, formatter = formatSeconds())
                    demoDefaults(initialOnlyDemo, ::inDuration)
                }

                options.register(ZoomifySettings.zoomOutTime) {
                    controller = slider(range = 0.1..5.0, step = 0.1, formatter = formatSeconds())
                    demoDefaults(initialOnlyDemo, ::outDuration)
                }

                options.register(ZoomifySettings.zoomInTransition) {
                    controller = enumSwitch()
                    demoDefaults(initialOnlyDemo, ::inTransition)
                }

                options.register(ZoomifySettings.zoomOutTransition) {
                    controller = enumSwitch()
                    demoDefaults(initialOnlyDemo, ::outTransition)
                }

                options.register(ZoomifySettings.affectHandFov) {
                    descriptionBuilder {
                        addDefaultText()
                        customImage(initialOnlyDemo)
                    }

                    controller = tickBox()

                    updateDemo { v, demo -> (demo as? FirstPersonDemo)?.keepHandFov = !v }
                }
            }

            val scrolling by groups.registering {
                val innerScrollOpts = mutableListOf<Option<*>>()

                options.register(ZoomifySettings.scrollZoom) {
                    controller = tickBox()

                    demoDefaults(scrollOnlyDemo, ::canScrollZoom)

                    listener { _, v -> innerScrollOpts.forEach { it.setAvailable(v) } }
                }

                options.register(ZoomifySettings.scrollZoomAmount) {
                    controller = slider(range = 1..10)
                    demoDefaults(scrollOnlyDemo, ::scrollZoomAmt)
                }.also { innerScrollOpts.add(it) }

                options.register(ZoomifySettings.scrollZoomSmoothness) {
                    controller = slider(range = 0..100, step = 1, formatter = { v: Int ->
                        if (v == 0)
                            Component.translatable("zoomify.gui.formatter.instant")
                        else
                            Component.literal("%d%%".format(v))
                    })
                    demoDefaults(scrollOnlyDemo, ::scrollZoomSmoothness)
                }.also { innerScrollOpts.add(it) }

                options.register(ZoomifySettings.linearLikeSteps) {
                    controller = tickBox()
                    demoDefaults(scrollOnlyDemo, ::linearLikeSteps)
                }.also { innerScrollOpts.add(it) }

                options.register(ZoomifySettings.retainZoomSteps) {
                    controller = tickBox()
                    demoDefaults(scrollOnlyDemo)
                }.also { innerScrollOpts.add(it) }

                innerScrollOpts.forEach { it.setAvailable(canScrollZoom) }
            }

            val spyglass by groups.registering {
                options.register(ZoomifySettings.spyglassBehaviour) {
                    defaultDescription()
                    controller = enumSwitch()
                }

                options.register(ZoomifySettings.spyglassOverlayVisibility) {
                    defaultDescription()
                    controller = enumSwitch()
                }

                options.register(ZoomifySettings.spyglassSoundBehaviour) {
                    defaultDescription()
                    controller = enumSwitch()
                }
            }
        }

        val controls by categories.registering {
            rootOptions.register(ZoomifySettings.zoomKeyBehaviour) {
                defaultDescription()
                controller = enumSwitch()
            }

            rootOptions.register(ZoomifySettings._keybindScrolling) {
                defaultDescription()
                controller = tickBox()
                flag(OptionFlag.GAME_RESTART)
            }

            rootOptions.register(ZoomifySettings.relativeSensitivity) {
                defaultDescription()
                controller = slider(range = 0..150, step = 10, formatter = { v: Int ->
                    if (v == 0)
                        CommonComponents.OPTION_OFF
                    else
                        Component.literal("%d%%".format(v))
                })
            }

            rootOptions.register(ZoomifySettings.relativeViewBobbing) {
                defaultDescription()
                controller = tickBox()
            }

            rootOptions.register(ZoomifySettings.cinematicCamera) {
                defaultDescription()
                controller = slider(range = 0..250, step = 10, formatter = { v: Int ->
                    if (v == 0)
                        CommonComponents.OPTION_OFF
                    else
                        Component.literal("%d%%".format(v))
                })
            }
        }

        val secondary by categories.registering {
            val infoLabel by rootOptions.registeringLabel

            rootOptions.register(ZoomifySettings.secondaryZoomAmount) {
                controller = slider(range = 2..10, step = 1, formatter = formatPercent())
                demoDefaults(secondaryZoomDemo, ::secondaryZoomAmount)
            }

            rootOptions.register(ZoomifySettings.secondaryZoomInTime) {
                controller = slider(range = 6.0..30.0, step = 2.0, formatter = formatSeconds())
                demoDefaults(secondaryZoomDemo, ::secondaryZoomInTime)
            }

            rootOptions.register(ZoomifySettings.secondaryZoomOutTime) {
                controller = slider(range = 0.0..5.0, step = 0.25, formatter = {
                    if (it == 0.0)
                        Component.translatable("zoomify.gui.formatter.instant")
                    else
                        Component.translatable("zoomify.gui.formatter.seconds", "%.2f".format(it))
                })
                demoDefaults(secondaryZoomDemo, ::secondaryZoomOutTime)
            }

            rootOptions.register(ZoomifySettings.secondaryHideHUDOnZoom) {
                descriptionBuilder {
                    addDefaultText()
                    customImage(secondaryZoomDemo)
                }

                controller = tickBox()

                updateDemo { v, demo -> (demo as? ThirdPersonDemo)?.renderHud = !v }
            }
        }

        val misc by categories.registering {
            val unbindConflicting by rootOptions.registeringButton {
                defaultDescription()
                action { _, _ ->
                    Zoomify.unbindConflicting()
                }
            }

            val checkMigrations by rootOptions.registeringButton {
                defaultDescription()
                action { _, _ ->
                    if (!Migrator.checkMigrations()) {
                        toast(
                            Component.translatable("zoomify.gui.title"),
                            Component.translatable("zoomify.migrate.no_migrations")
                        )
                    }
                }
            }

            val presets by groups.registering {
                val applyWarning by options.registeringLabel

                for (preset in Presets.entries) {
                    options.registerButton(preset.name.lowercase()) {
                        name(preset.displayName)

                        action { screen, _ ->
                            val minecraft = Minecraft.getInstance()
                            preset.apply(ZoomifySettings)

                            toast(
                                Component.translatable("zoomify.gui.preset.toast.title"),
                                Component.translatable("zoomify.gui.preset.toast.description", preset.displayName)
                            )

                            OptionUtils.forEachOptions(screen.config, Option<*>::forgetPendingValue)
                            ZoomifySettings.saveToFile()
                            screen.init(minecraft, screen.width, screen.height)
                        }
                    }
                }
            }
        }
    }.generateScreen(parent)

    private fun <T> OptionDsl<T>.demoDefaults(demo: ZoomDemoImageRenderer, prop: KMutableProperty0<T>? = null) {
        descriptionBuilder {
            addDefaultText()
            customImage(demo)
        }

        prop?.let { updateDemo(it) }
    }
}

private fun <T : Number> formatSeconds() = ValueFormatter<T> {
    Component.translatable("zoomify.gui.formatter.seconds", "%.1f".format(it))
}

private fun <T : Number> formatPercent() = ValueFormatter<T> {
    Component.literal("%dx".format(it))
}

private fun OptionDsl<*>.defaultDescription() {
    descriptionBuilder {
        addDefaultText()
    }
}

private fun ButtonOptionDsl.defaultDescription() {
    descriptionBuilder {
        addDefaultText()
    }
}
