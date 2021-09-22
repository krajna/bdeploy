import { Component, Input, OnDestroy, OnInit } from '@angular/core';
import { BehaviorSubject, combineLatest, Subscription } from 'rxjs';
import { BdDataGrouping } from 'src/app/models/data';
import { ApplicationConfiguration, ApplicationStartType, InstanceNodeConfigurationDto, MinionStatusDto, ProcessStatusDto } from 'src/app/models/gen.dtos';
import { InstancesService } from '../../../services/instances.service';
import { NodeApplicationPort, PortsService } from '../../../services/ports.service';
import { ProcessesService } from '../../../services/processes.service';
import { StateItem, StateType } from './state-panel/state-panel.component';

@Component({
  selector: 'app-instance-server-node',
  templateUrl: './server-node.component.html',
  styleUrls: ['./server-node.component.css'],
})
export class ServerNodeComponent implements OnInit, OnDestroy {
  @Input() node: InstanceNodeConfigurationDto;

  @Input() gridWhen$: BehaviorSubject<boolean>;
  @Input() groupingWhen$: BehaviorSubject<BdDataGrouping<ApplicationConfiguration>[]>;
  @Input() collapsedWhen$: BehaviorSubject<boolean>;
  @Input() narrowWhen$: BehaviorSubject<boolean>;

  /* template */ nodeState$ = new BehaviorSubject<MinionStatusDto>(null);
  /* template */ nodeStateItems$ = new BehaviorSubject<StateItem[]>([]);

  private subscription: Subscription;
  private portsState = new BehaviorSubject<StateType>('unknown');
  private portsTooltip = new BehaviorSubject<string>('State of all server ports');
  private portsItem: StateItem = { name: 'Server Ports', type: this.portsState, tooltip: this.portsTooltip };

  private processesState = new BehaviorSubject<StateType>('unknown');
  private processesTooltip = new BehaviorSubject<string>('State of all server processes');
  private processesItem: StateItem = { name: 'Instance Processes', type: this.processesState, tooltip: this.processesTooltip };

  constructor(private instances: InstancesService, private ports: PortsService, private processes: ProcessesService) {}

  ngOnInit(): void {
    this.subscription = this.instances.activeNodeStates$.subscribe((states) => {
      if (!states || !states[this.node.nodeName]) {
        this.nodeState$.next(null);
        this.nodeStateItems$.next([]);
        return;
      }
      const state = states[this.node.nodeName];
      this.nodeState$.next(state);

      const items: StateItem[] = [];
      items.push({ name: state.offline ? 'Offline' : 'Online', type: state.offline ? 'warning' : 'ok' });
      items.push(this.processesItem);
      items.push(this.portsItem);

      this.nodeStateItems$.next(items);
    });

    this.subscription.add(
      combineLatest([this.ports.activePortStates$, this.processes.processStates$]).subscribe(([ports, states]) => {
        this.updateAllProcesses(states);
        this.updateAllPortsRating(ports, states);
      })
    );
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }

  private updateAllProcesses(states: { [key: string]: ProcessStatusDto }) {
    if (!states) {
      this.processesState.next('unknown');
      this.processesTooltip.next('No information available');
      return;
    }

    let badApps = 0;
    this.node.nodeConfiguration.applications.forEach((app) => {
      if (app.processControl.startType === ApplicationStartType.INSTANCE) {
        if (!ProcessesService.isRunning(ProcessesService.get(states, app.uid)?.processState)) {
          badApps++;
        }
      }
    });

    this.processesState.next(!badApps ? 'ok' : 'warning');
    this.processesTooltip.next(!badApps ? 'All applications OK' : `${badApps} 'Instance' type applications are not running.`);
  }

  private updateAllPortsRating(ports: NodeApplicationPort[], states: { [key: string]: ProcessStatusDto }) {
    if (!ports || !states) {
      this.portsState.next('unknown');
      this.portsTooltip.next('No information available');
      return;
    }

    const appPorts = ports.filter((p) => !!this.node.nodeConfiguration.applications.find((a) => a.uid === p.appUid));

    let badPorts = 0;
    appPorts.forEach((p) => {
      const process = ProcessesService.get(states, p.appUid);
      if (ProcessesService.isRunning(process.processState) !== p.state) {
        badPorts++;
      }
    });

    this.portsState.next(!badPorts ? 'ok' : 'warning');
    this.portsTooltip.next(!badPorts ? `All ${appPorts.length} server ports OK` : `${badPorts} of ${appPorts.length} server ports are rated bad.`);
  }
}