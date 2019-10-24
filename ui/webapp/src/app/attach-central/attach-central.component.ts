import { Location } from '@angular/common';
import { Component, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { MatStep, MatStepper } from '@angular/material';
import { EventSourcePolyfill } from 'ng-event-source';
import { AttachIdentDto, InstanceGroupConfiguration } from '../models/gen.dtos';
import { ConfigService } from '../services/config.service';
import { DownloadService } from '../services/download.service';
import { InstanceGroupService } from '../services/instance-group.service';
import { ErrorMessage, LoggingService } from '../services/logging.service';
import { RemoteEventsService } from '../services/remote-events.service';

@Component({
  selector: 'app-attach-central',
  templateUrl: './attach-central.component.html',
  styleUrls: ['./attach-central.component.css'],
})
export class AttachCentralComponent implements OnInit, OnDestroy {
  // inter-browser support only works with text/plain...
  static readonly ATTACH_MIME_TYPE = 'text/plain';

  private log = this.logging.getLogger('AttachCentralComponent');
  attachPayload: AttachIdentDto;
  updateEvents: EventSourcePolyfill;
  remoteAttached: InstanceGroupConfiguration;
  manualLoading = false;

  @ViewChild(MatStepper, { static: true })
  stepper: MatStepper;

  @ViewChild('doneStep', { static: true })
  doneStep: MatStep;

  constructor(
    public location: Location,
    private config: ConfigService,
    private eventService: RemoteEventsService,
    private logging: LoggingService,
    private igService: InstanceGroupService,
    private dlService: DownloadService,
  ) {}

  ngOnInit() {
    this.config.getAttachIdent().subscribe(i => (this.attachPayload = i));

    this.updateEvents = this.eventService.getAttachEventSource();
    this.updateEvents.onerror = err => {
      this.log.error(new ErrorMessage('Error waiting for attach events', err));
    };
    this.updateEvents.addEventListener('attach', e => this.onRemoteAttach(e as MessageEvent));
  }

  ngOnDestroy() {
    if (this.updateEvents) {
      this.updateEvents.close();
      this.updateEvents = null;
    }
  }

  onRemoteAttach(e: MessageEvent) {
    const groupName = e.data as string;
    this.igService.getInstanceGroup(groupName).subscribe(r => {
      this.remoteAttached = r;
      this.stepper.selected = this.doneStep;
    });
  }

  onDragStart($event) {
    $event.dataTransfer.effectAllowed = 'link';
    $event.dataTransfer.setData(AttachCentralComponent.ATTACH_MIME_TYPE, JSON.stringify(this.attachPayload));
  }

  downloadManualJson() {
    this.dlService.downloadJson('server-' + this.attachPayload.name + '.json', this.attachPayload);
  }

  async onDrop($event: DragEvent) {
    $event.preventDefault();

    let data;
    if ($event.dataTransfer.files.length > 0) {
      await new Promise((resolve, reject) => {
        const reader = new FileReader();
        reader.onload = (e) => { data = reader.result.toString(); resolve(); };
        reader.onerror = (e) => reject();
        reader.readAsText($event.dataTransfer.files[0]);
      });
    } else if ($event.dataTransfer.types.includes(AttachCentralComponent.ATTACH_MIME_TYPE)) {
      data = $event.dataTransfer.getData(AttachCentralComponent.ATTACH_MIME_TYPE);
    }

    this.manualLoading = true;

    this.config.manualAttachCentral(data).subscribe(group => {
      this.igService.getInstanceGroup(group).subscribe(r => {
        this.remoteAttached = r;
        this.stepper.selected = this.doneStep;
      });
    });
  }

  onOver($event: DragEvent) {
    // need to cancel the event and return false to ALLOW drop.
    if ($event.preventDefault) {
      $event.preventDefault();
    }

    return false;
  }
}
