import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { LauncherDto, ManifestKey, NodeStatus } from '../models/gen.dtos';
import { ConfigService } from './config.service';
import { Logger, LoggingService } from './logging.service';

@Injectable({
  providedIn: 'root'
})
export class SoftwareUpdateService {

  private static BASEPATH = '/swup';
  private readonly log: Logger = this.loggingService.getLogger('SoftwareUpdateService');

  constructor(
    private cfg: ConfigService,
    private http: HttpClient,
    private loggingService: LoggingService,
  ) {}

  public listBDeployVersions(): Observable<ManifestKey[]> {
    const url: string = this.cfg.config.api + SoftwareUpdateService.BASEPATH + '/bdeploy';
    this.log.debug('listBDeployVersions: ' + url);
    return this.http.get<ManifestKey[]>(url);
  }

  public listLauncherVersions(): Observable<ManifestKey[]> {
    const url: string = this.cfg.config.api + SoftwareUpdateService.BASEPATH + '/launcher';
    this.log.debug('listLauncherVersions: ' + url);
    return this.http.get<ManifestKey[]>(url);
  }

  public getLatestLaunchers(): Observable<LauncherDto> {
    const url: string = this.cfg.config.api + SoftwareUpdateService.BASEPATH + '/launcherLatest';
    this.log.debug('getLatestLaunchers: ' + url);
    return this.http.get<LauncherDto>(url);
  }

  public deleteVersion(keys: ManifestKey[]) {
    const url: string = this.cfg.config.api + SoftwareUpdateService.BASEPATH;
    this.log.debug('deleteVersion: ' + url);
    return this.http.post(url, keys);
  }

  public getNodeStates() {
    const url: string = this.cfg.config.api + SoftwareUpdateService.BASEPATH + '/bdeploy/minions';
    this.log.debug('getNodeStates: ' + url);
    return this.http.get<NodeStatus[]>(url);
  }

  public updateBdeploy(keys: ManifestKey[]) {
    const url: string = this.cfg.config.api + SoftwareUpdateService.BASEPATH + '/selfUpdate';
    this.log.debug('updateBdeploy: ' + url);
    return this.http.post(url, keys);
  }

  public getUploadUrl() {
    return this.cfg.config.api + SoftwareUpdateService.BASEPATH;
  }

  public getDownloadUrl(key: ManifestKey) {
    return this.cfg.config.api + SoftwareUpdateService.BASEPATH + '/download/' + key.name + '/' + key.tag;
  }

}