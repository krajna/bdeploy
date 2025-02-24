import { Component, OnInit, ViewChild } from '@angular/core';
import { Base64 } from 'js-base64';
import { BehaviorSubject, Observable } from 'rxjs';
import { finalize } from 'rxjs/operators';
import { BdDialogToolbarComponent } from 'src/app/modules/core/components/bd-dialog-toolbar/bd-dialog-toolbar.component';
import { BdDialogComponent } from 'src/app/modules/core/components/bd-dialog/bd-dialog.component';
import { DirtyableDialog } from 'src/app/modules/core/guards/dirty-dialog.guard';
import { LoggingAdminService } from 'src/app/modules/primary/admin/services/logging-admin.service';

@Component({
  selector: 'app-log-config-editor',
  templateUrl: './log-config-editor.component.html',
  styleUrls: ['./log-config-editor.component.css'],
})
export class LogConfigEditorComponent implements OnInit, DirtyableDialog {
  /* template */ loading$ = new BehaviorSubject<boolean>(true);
  /* template */ config: string;
  private origConfig: string;

  @ViewChild(BdDialogComponent) public dialog: BdDialogComponent;
  @ViewChild(BdDialogToolbarComponent) private tb: BdDialogToolbarComponent;

  constructor(private loggingAdmin: LoggingAdminService) {}

  ngOnInit(): void {
    this.loggingAdmin
      .getLogConfig()
      .pipe(finalize(() => this.loading$.next(false)))
      .subscribe((config) => {
        this.config = Base64.decode(config);
        this.origConfig = this.config;
      });
  }

  isDirty(): boolean {
    return this.config !== this.origConfig;
  }

  doSave(): Observable<any> {
    return this.loggingAdmin.setLogConfig(Base64.encode(this.config));
  }

  /* template */ onSave() {
    this.doSave().subscribe((_) => this.tb.closePanel());
  }
}
