import { defer, Observable, throwError } from 'rxjs';
import { catchError, finalize, tap } from 'rxjs/operators';

/**
 * Creates an Observable that mirrors the source Observable and adds performance marks for:
 * - subscribe: when an Observer subscribes to the source Observable.
 * - next: when the source Observable emits a next notification.
 * - error: when the source Observable emits an error notification.
 * - complete: when the source Observer terminates on complete or error.
 *
 * It will also create a measurement to measure the time between subscribe and complete.
 * This measurement along with the rolling average for the same name will be logged to the console.
 *
 * Note that this only works on Observables which complete by design, thus it makes no sense on
 * (e.g.) BehaviorSubject which never completes.
 *
 * @param name The name for the marks and measurement.
 */
export const measure = function <T>(name: string) {
  const prefix = 'bdeploy:';
  const nativeWindow = window;
  const fullName = prefix + name;
  return (source: Observable<T>) => {
    if ('performance' in nativeWindow && nativeWindow.performance !== undefined) {
      return defer(() => {
        nativeWindow.performance.mark(`${fullName}:subscribe`);
        return source.pipe(
          tap(() => nativeWindow.performance.mark(`${fullName}:next`)),
          catchError((error) => {
            nativeWindow.performance.mark(`${fullName}:error`);
            return throwError(error);
          }),
          finalize(() => {
            nativeWindow.performance.mark(`${fullName}:complete`);
            nativeWindow.performance.measure(`${fullName}`, `${fullName}:subscribe`, `${fullName}:complete`);
            nativeWindow.performance.measure(`${fullName}-JSP`, `${fullName}:next`, `${fullName}:complete`);
            logMeasurements(
              name,
              nativeWindow.performance.getEntriesByName(fullName, 'measure'),
              nativeWindow.performance.getEntriesByName(fullName + '-JSP', 'measure')
            );
          })
        );
      });
    }
    return source;
  };
};

function logMeasurements(name: string, entries: PerformanceEntryList, jspEntries: PerformanceEntryList) {
  const last = entries[entries.length - 1];
  const avg = entries.map((p) => p.duration).reduce((p, c) => p + c) / entries.length;
  const lastJsp = jspEntries[entries.length - 1];
  console.group(name);
  logTiming('Total Duration [ms]', last.duration);
  logTiming('JS Processing [ms]', lastJsp.duration);
  logTiming('Average Total [ms]', avg);
  console.groupEnd();
}

function logTiming(label: string, duration: number) {
  if (duration > 300) {
    console.warn(label, duration);
  } else {
    console.log(label, duration);
  }
}
