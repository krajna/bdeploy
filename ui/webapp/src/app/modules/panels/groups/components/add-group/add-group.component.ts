import { Component, OnInit, ViewChild } from '@angular/core';
import { BehaviorSubject } from 'rxjs';
import { finalize } from 'rxjs/operators';
import { InstanceGroupConfiguration } from 'src/app/models/gen.dtos';
import { BdDialogComponent } from 'src/app/modules/core/components/bd-dialog/bd-dialog.component';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { GroupsService } from 'src/app/modules/primary/groups/services/groups.service';

@Component({
  selector: 'app-add-group',
  templateUrl: './add-group.component.html',
  styleUrls: ['./add-group.component.css'],
})
export class AddGroupComponent implements OnInit {
  /* template */ saving$ = new BehaviorSubject<boolean>(false);
  /* template */ group: Partial<InstanceGroupConfiguration> = { autoDelete: true };

  @ViewChild(BdDialogComponent) dialog: BdDialogComponent;

  private image: File;

  constructor(private groups: GroupsService, private areas: NavAreasService) {}

  ngOnInit(): void {}

  /* template */ onSelectImage(image: File) {
    this.image = image;
  }

  /* template */ onUnsupportedFile(file: File) {
    this.dialog.info('Unsupported File Type', `${file.name} has an unsupported file type.`, 'warning');
  }

  /* template */ onSave() {
    this.saving$.next(true);
    this.groups.create(this.group).subscribe((_) => {
      if (!!this.image) {
        this.groups
          .updateImage(this.group.name, this.image)
          .pipe(
            finalize(() => {
              this.saving$.next(false);
              this.areas.closePanel();
            })
          )
          .subscribe((__) => {});
      } else {
        this.saving$.next(false);
        this.areas.closePanel();
      }
    });
  }
}
