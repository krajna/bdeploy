import { Component, OnDestroy, OnInit } from '@angular/core';
import { BehaviorSubject, Subscription } from 'rxjs';
import { BdDataColumn } from 'src/app/models/data';
import { InstanceVersionDto } from 'src/app/models/gen.dtos';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { InstancesService } from 'src/app/modules/primary/instances/services/instances.service';
import { HistoryDetailsService } from '../../services/history-details.service';

@Component({
  selector: 'app-history-compare-select',
  templateUrl: './history-compare-select.component.html',
  styleUrls: ['./history-compare-select.component.css'],
})
export class HistoryCompareSelectComponent implements OnInit, OnDestroy {
  private versionColumn: BdDataColumn<InstanceVersionDto> = {
    id: 'version',
    name: 'Instance Version',
    data: (r) => this.getVersionText(r),
    width: '100px',
    classes: (r) => this.getVersionClass(r),
  };

  private productVersionColumn: BdDataColumn<InstanceVersionDto> = {
    id: 'prodVersion',
    name: 'Product Version',
    data: (r) => r.product.tag,
  };

  private subscription: Subscription;
  private key: string;

  /* template */ records$ = new BehaviorSubject<InstanceVersionDto[]>(null);
  /* template */ base: string;
  /* template */ columns: BdDataColumn<InstanceVersionDto>[] = [this.versionColumn, this.productVersionColumn];
  /* template */ getRecordRoute = (row: InstanceVersionDto) => {
    if (row.key.tag === this.base) {
      return [];
    }
    return ['', { outlets: { panel: ['panels', 'instances', 'history', this.key, 'compare', this.base, row.key.tag] } }];
  };

  constructor(private areas: NavAreasService, public details: HistoryDetailsService, private instances: InstancesService) {
    this.subscription = this.areas.panelRoute$.subscribe((r) => {
      if (!r) {
        return;
      }
      this.base = r.paramMap.get('base');
      this.key = r.paramMap.get('key');
    });

    this.subscription.add(
      this.details.versions$.subscribe((versions) => {
        if (!versions?.length) {
          this.records$.next(null);
        } else {
          this.records$.next(this.doSort(versions));
        }
      })
    );
  }

  ngOnInit(): void {}

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }

  private getVersionText(row: InstanceVersionDto) {
    if (row.key.tag === this.base) {
      return `${row.key.tag} - Selected`;
    }

    if (row.key.tag === this.instances.current$.value?.activeVersion?.tag) {
      return `${row.key.tag} - Active`;
    }

    if (row.key.tag === this.instances.current$.value?.instance?.tag) {
      return `${row.key.tag} - Current`;
    }

    return row.key.tag;
  }

  private getVersionClass(row: InstanceVersionDto): string[] {
    if (row.key.tag === this.base) {
      return ['bd-warning-text'];
    }

    if (row.key.tag === this.instances.current$.value?.activeVersion?.tag) {
      return ['bd-accent-text'];
    }

    if (row.key.tag === this.instances.current$.value?.instance?.tag) {
      return ['bd-accent-text'];
    }

    return [];
  }

  /* template */ onBack() {
    window.history.back();
  }

  /* template */ doSort(records: InstanceVersionDto[]) {
    return [...records].sort((a, b) => Number(b.key.tag) - Number(a.key.tag));
  }
}
