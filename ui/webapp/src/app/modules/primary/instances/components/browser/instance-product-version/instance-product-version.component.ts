import { Component, Input, OnInit } from '@angular/core';
import { InstanceDto } from 'src/app/models/gen.dtos';

@Component({
  selector: 'app-instance-product-version',
  templateUrl: './instance-product-version.component.html',
  styleUrls: ['./instance-product-version.component.css'],
})
export class InstanceProductVersionComponent implements OnInit {
  @Input() record: InstanceDto;

  constructor() {}

  ngOnInit(): void {}
}
