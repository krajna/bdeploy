import { Component, OnDestroy, OnInit } from '@angular/core';
import { Subscription } from 'rxjs';
import { BdDataGroupingDefinition } from 'src/app/models/data';
import { InstanceGroupConfiguration } from 'src/app/models/gen.dtos';
import { AuthenticationService } from 'src/app/modules/core/services/authentication.service';
import { ConfigService } from 'src/app/modules/core/services/config.service';
import { GroupsColumnsService } from '../../services/groups-columns.service';
import { GroupsService } from '../../services/groups.service';

@Component({
  selector: 'app-groups-browser',
  templateUrl: './groups-browser.component.html',
  styleUrls: ['./groups-browser.component.css'],
})
export class GroupsBrowserComponent implements OnInit, OnDestroy {
  grouping: BdDataGroupingDefinition<InstanceGroupConfiguration>[] = [];

  private subscription: Subscription;

  /* template */ getRecordRoute = (row: InstanceGroupConfiguration) => {
    if (this.authService.isScopedExclusiveReadClient(row.name)) {
      return ['/groups', 'clients', row.name];
    }
    return ['/instances', 'browser', row.name];
  };

  constructor(
    public groups: GroupsService,
    public groupColumns: GroupsColumnsService,
    public config: ConfigService,
    public authService: AuthenticationService
  ) {}

  ngOnInit(): void {
    this.subscription = this.groups.attributeDefinitions$.subscribe((attrs) => {
      this.grouping = attrs.map((attr) => {
        return {
          name: attr.description,
          group: (r) => this.groups.attributeValues$.value[r.name]?.attributes[attr.name],
        };
      });
    });
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }

  /* template */ isAddAllowed(): boolean {
    return this.authService.isGlobalAdmin() && (this.config.isCentral() || this.config.isStandalone());
  }

  /* template */ isAttachAllowed(): boolean {
    return this.authService.isGlobalAdmin() && this.config.isManaged();
  }
}
