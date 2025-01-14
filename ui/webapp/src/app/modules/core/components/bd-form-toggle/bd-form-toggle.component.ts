import { Component, Input, OnInit, Optional, Self, ViewChild, ViewEncapsulation } from '@angular/core';
import { ControlValueAccessor, NgControl } from '@angular/forms';
import { MatCheckbox } from '@angular/material/checkbox';
import { MatSlideToggle } from '@angular/material/slide-toggle';

@Component({
  selector: 'app-bd-form-toggle',
  templateUrl: './bd-form-toggle.component.html',
  styleUrls: ['./bd-form-toggle.component.css'],
  encapsulation: ViewEncapsulation.None,
})
export class BdFormToggleComponent implements OnInit, ControlValueAccessor {
  @Input() label: string;
  @Input() name: string;
  @Input() disabled: boolean;
  @Input() appearance: 'slide' | 'checkbox' = 'checkbox';

  @ViewChild(MatCheckbox, { static: false }) private checkbox: MatCheckbox;
  @ViewChild(MatSlideToggle, { static: false }) private slide: MatSlideToggle;

  /* template */ get value() {
    return this.internalValue;
  }
  /* template */ set value(v) {
    if (v !== this.internalValue) {
      this.internalValue = v;
      this.onTouchedCb();
      this.onChangedCb(v);
    }
  }

  private internalValue: any = '';
  private onTouchedCb: () => void = () => {};
  private onChangedCb: (_: any) => void = () => {};

  constructor(@Optional() @Self() private ngControl: NgControl) {
    if (!!ngControl) {
      ngControl.valueAccessor = this;
    }
  }

  ngOnInit(): void {}

  writeValue(v: any): void {
    if (v !== this.internalValue) {
      this.internalValue = v;
    }
  }

  registerOnChange(fn: any): void {
    this.onChangedCb = fn;
  }

  registerOnTouched(fn: any): void {
    this.onTouchedCb = fn;
  }

  /* template */
  onClick() {
    if (this.disabled) {
      return;
    }
    if (!!this.checkbox) {
      this.checkbox.toggle();
      this.value = this.checkbox.checked;
    }
    if (!!this.slide) {
      this.slide.toggle();
      this.value = this.slide.checked;
    }
  }
}
