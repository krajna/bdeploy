import { Component, Input, OnInit, Optional, Self, ViewEncapsulation } from '@angular/core';
import { ControlValueAccessor, FormControl, FormGroupDirective, NgControl, NgForm } from '@angular/forms';
import { ErrorStateMatcher } from '@angular/material/core';
import { bdValidationMessage } from '../../validators/messages';

@Component({
  selector: 'app-bd-form-input',
  templateUrl: './bd-form-input.component.html',
  styleUrls: ['./bd-form-input.component.css'],
  encapsulation: ViewEncapsulation.None,
})
export class BdFormInputComponent implements OnInit, ControlValueAccessor, ErrorStateMatcher {
  @Input() label: string;
  @Input() name: string;
  @Input() required: any;
  @Input() disabled: any;
  @Input() type: string;
  @Input() errorDisplay: 'touched' | 'immediate' = 'touched';

  /* template */ get value() {
    return this.internalValue;
  }
  /* template */ set value(v) {
    if (v !== this.internalValue) {
      this.internalValue = v;
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

  onBlur() {
    this.onTouchedCb();
  }

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

  isErrorState(control: FormControl | null, form: FormGroupDirective | NgForm | null): boolean {
    if (!this.isInvalid()) {
      return false;
    }

    return this.errorDisplay === 'immediate' || !!(control && (control.dirty || control.touched));
  }

  /* template */ isInvalid() {
    if (!this.ngControl) {
      return false;
    }

    return this.ngControl.invalid;
  }

  /* template */ getErrorMessage() {
    if (!this.ngControl) {
      return null;
    }

    return bdValidationMessage(this.label, this.ngControl.errors);
  }
}
