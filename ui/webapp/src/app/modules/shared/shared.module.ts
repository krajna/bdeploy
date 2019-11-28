import { CommonModule } from '@angular/common';
import { NgModule } from '@angular/core';
import { CoreModule } from '../core/core.module';
import { FileUploadComponent } from './components/file-upload/file-upload.component';
import { FileViewerComponent } from './components/file-viewer/file-viewer.component';
import { MessageboxComponent } from './components/messagebox/messagebox.component';
import { RemoteProgressElementComponent } from './components/remote-progress-element/remote-progress-element.component';
import { RemoteProgressComponent } from './components/remote-progress/remote-progress.component';

@NgModule({
  declarations: [
    RemoteProgressComponent,
    RemoteProgressElementComponent,
    FileViewerComponent,
    MessageboxComponent,
    FileUploadComponent,
  ],
  entryComponents: [
    MessageboxComponent,
    FileUploadComponent,
  ],
  imports: [
    CommonModule,
    CoreModule,
  ],
  exports: [
    RemoteProgressComponent,
    FileViewerComponent,
    MessageboxComponent,
    FileUploadComponent,
  ]
})
export class SharedModule { }
