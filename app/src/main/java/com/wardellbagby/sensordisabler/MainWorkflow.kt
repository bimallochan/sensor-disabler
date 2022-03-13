package com.wardellbagby.sensordisabler

import android.content.Context
import android.hardware.Sensor
import android.os.Parcelable
import com.squareup.workflow1.Snapshot
import com.squareup.workflow1.StatefulWorkflow
import com.squareup.workflow1.action
import com.squareup.workflow1.renderChild
import com.squareup.workflow1.ui.ViewRegistry
import com.squareup.workflow1.ui.modal.AlertContainer
import com.squareup.workflow1.ui.toParcelable
import com.squareup.workflow1.ui.toSnapshot
import com.wardellbagby.sensordisabler.MainWorkflow.Props
import com.wardellbagby.sensordisabler.MainWorkflow.State
import com.wardellbagby.sensordisabler.MainWorkflow.State.*
import com.wardellbagby.sensordisabler.sensordetail.SensorDetailLayoutRunner
import com.wardellbagby.sensordisabler.sensordetail.SensorDetailWorkflow
import com.wardellbagby.sensordisabler.sensordetail.SensorDetailWorkflow.Output.BackPressed
import com.wardellbagby.sensordisabler.sensordetail.SensorDetailWorkflow.Output.Saved
import com.wardellbagby.sensordisabler.sensorlist.SensorListLayoutRunner
import com.wardellbagby.sensordisabler.sensorlist.SensorListWorkflow
import com.wardellbagby.sensordisabler.sensorlist.SensorListWorkflow.Output.SelectedSensor
import com.wardellbagby.sensordisabler.settings.Output.Closed
import com.wardellbagby.sensordisabler.settings.SettingsLayoutRunner
import com.wardellbagby.sensordisabler.settings.SettingsWorkflow
import com.wardellbagby.sensordisabler.toolbar.ToolbarAction
import com.wardellbagby.sensordisabler.toolbar.ToolbarLayoutRunner
import com.wardellbagby.sensordisabler.toolbar.ToolbarProps
import com.wardellbagby.sensordisabler.toolbar.ToolbarWorkflow
import com.wardellbagby.sensordisabler.util.getMockedValues
import com.wardellbagby.sensordisabler.util.getModificationType
import com.wardellbagby.sensordisabler.util.saveSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.scopes.ActivityRetainedScoped
import kotlinx.parcelize.Parcelize
import javax.inject.Inject
import com.wardellbagby.sensordisabler.sensordetail.SensorDetailWorkflow.Props as DetailProps
import com.wardellbagby.sensordisabler.sensorlist.SensorListWorkflow.Props as ListProps

val MainViewRegistry = ViewRegistry(
  ToolbarLayoutRunner,
  SensorDetailLayoutRunner,
  DrawerLayoutRunner,
  SensorListLayoutRunner,
  AlertContainer,
  SettingsLayoutRunner
)

@ActivityRetainedScoped
class MainWorkflow
@Inject constructor(
  @ApplicationContext private val androidContext: Context,
  private val sensorListWorkflow: SensorListWorkflow,
  private val sensorDetailWorkflow: SensorDetailWorkflow,
  private val settingsWorkflow: SettingsWorkflow,
  private val toaster: Toaster
) : StatefulWorkflow<Props, State, Nothing, Any>() {

  data class Props(val sensors: List<Sensor>)
  sealed class State : Parcelable {
    abstract val sensorIndex: Int

    @Parcelize
    data class SensorList(override val sensorIndex: Int) : State()

    @Parcelize
    data class SensorDetails(override val sensorIndex: Int) : State()

    @Parcelize
    data class AppSettings(override val sensorIndex: Int) : State()
  }

  override fun initialState(
    props: Props,
    snapshot: Snapshot?
  ): State = snapshot?.toParcelable() ?: SensorList(sensorIndex = 0)

  override fun render(
    renderProps: Props,
    renderState: State,
    context: RenderContext
  ): Any {
    val sink = context.actionSink

    return when (renderState) {
      is SensorList, is SensorDetails -> {
        val drawerRendering =
          context.renderChild(
            sensorListWorkflow,
            props = ListProps(renderProps.sensors),
            handler = {
              when (it) {
                is SelectedSensor -> action {
                  state = SensorDetails(it.sensorIndex)
                }
                is SensorListWorkflow.Output.BackPressed -> action {
                  state = SensorDetails(state.sensorIndex)
                }
              }
            })

        val sensor = renderProps.sensors[renderState.sensorIndex]
        val contentRendering = context.renderChild(
          sensorDetailWorkflow,
          props = DetailProps(
            sensor = sensor,
            modificationType = sensor.getModificationType(androidContext),
            defaultMockableValues = sensor.getMockedValues(androidContext)
          ),
          handler = {
            when (it) {
              is Saved -> action {
                renderProps.sensors[renderState.sensorIndex].saveSettings(
                  androidContext, it.modificationType
                )
                toaster.showToast(androidContext.getString(R.string.settings_saved))
              }
              is BackPressed -> action {
                state = SensorList(state.sensorIndex)
              }
            }
          }
        )

        DrawerLayoutRendering(
          drawerRendering = drawerRendering,
          contentRendering = OptionalToolbarScreen(
            toolbar = context.renderChild(
              ToolbarWorkflow,
              props = contentRendering.beneathModals.toolbarProps.addSettings(context)
            ),
            content = contentRendering
          ),
          isDrawerOpened = renderState is SensorList,
          onDrawerClosed = {
            if (renderState !is SensorList) return@DrawerLayoutRendering
            sink.send(
              action {
                state = SensorDetails(state.sensorIndex)
              })
          }
        )
      }
      is AppSettings -> context.renderChild(
        settingsWorkflow,
        props = SettingsWorkflow.Props(renderProps.sensors),
        handler = {
          when (it) {
            Closed -> action { state = SensorDetails(state.sensorIndex) }
          }
        })
    }
  }

  override fun snapshotState(state: State): Snapshot {
    return state.toSnapshot()
  }

  private fun ToolbarProps.addSettings(context: RenderContext): ToolbarProps {
    val settings = ToolbarAction(
      R.drawable.ic_settings,
      androidContext.getString(R.string.menu_settings),
      onClick = context.eventHandler {
        state = AppSettings(state.sensorIndex)
      }
    )

    return copy(overflowMenu = overflowMenu + settings)
  }
}