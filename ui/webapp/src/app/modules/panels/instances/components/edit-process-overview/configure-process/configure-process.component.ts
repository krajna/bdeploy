import { Component, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { Subscription } from 'rxjs';
import { BdDialogToolbarComponent } from 'src/app/modules/core/components/bd-dialog-toolbar/bd-dialog-toolbar.component';
import { BdDialogComponent } from 'src/app/modules/core/components/bd-dialog/bd-dialog.component';
import { DirtyableDialog } from 'src/app/modules/core/guards/dirty-dialog.guard';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { InstanceEditService } from 'src/app/modules/primary/instances/services/instance-edit.service';
import { ProcessEditService } from '../../../services/process-edit.service';
import { ConfigProcessHeaderComponent } from './config-process-header/config-process-header.component';
import { ConfigProcessParamGroupComponent } from './config-process-param-group/config-process-param-group.component';

@Component({
  selector: 'app-configure-process',
  templateUrl: './configure-process.component.html',
  styleUrls: ['./configure-process.component.css'],
})
export class ConfigureProcessComponent implements OnInit, OnDestroy, DirtyableDialog {
  @ViewChild(BdDialogToolbarComponent) private tb: BdDialogToolbarComponent;
  @ViewChild(BdDialogComponent) public dialog: BdDialogComponent;

  @ViewChild(ConfigProcessHeaderComponent) private cfgHeader: ConfigProcessHeaderComponent;
  @ViewChild(ConfigProcessParamGroupComponent) private cfgParams: ConfigProcessParamGroupComponent;

  private subscription: Subscription;

  constructor(public edit: ProcessEditService, public instanceEdit: InstanceEditService, areas: NavAreasService) {
    this.subscription = areas.registerDirtyable(this, 'panel');
  }

  ngOnInit(): void {}

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }

  public isDirty(): boolean {
    return this.instanceEdit.hasPendingChanges();
  }

  /* template */ doApply() {
    this.edit.alignGlobalParameters(this.edit.application$.value, this.edit.process$.value);
    this.instanceEdit.conceal(`Edit ${this.edit.process$.value.name}`);
    this.tb.closePanel();
  }

  /* template */ isInvalid(): boolean {
    return this.cfgHeader.isInvalid() || this.cfgParams.isInvalid();
  }
}