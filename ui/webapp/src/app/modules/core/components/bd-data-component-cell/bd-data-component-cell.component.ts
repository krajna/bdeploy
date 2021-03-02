import {
  AfterViewInit,
  Component,
  ComponentFactoryResolver,
  ComponentRef,
  Input,
  OnDestroy,
  OnInit,
  Type,
  ViewContainerRef,
} from '@angular/core';

@Component({
  selector: 'app-bd-data-component-cell',
  templateUrl: './bd-data-component-cell.component.html',
  styleUrls: ['./bd-data-component-cell.component.css'],
})
export class BdDataComponentCellComponent<T, X> implements OnInit, OnDestroy, AfterViewInit {
  @Input() record: T;
  @Input() componentType: Type<X>;
  private componentRef: ComponentRef<X>;

  constructor(private resolver: ComponentFactoryResolver, private vc: ViewContainerRef) {}

  ngOnInit(): void {
    this.vc.clear();
    const factory = this.resolver.resolveComponentFactory(this.componentType);
    this.componentRef = this.vc.createComponent(factory);
    this.componentRef.instance['record'] = this.record;
  }

  ngAfterViewInit(): void {}

  ngOnDestroy(): void {
    if (!this.componentRef) {
      this.componentRef.destroy();
    }
  }
}
