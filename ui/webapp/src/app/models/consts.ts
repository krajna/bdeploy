/** HTTP header constant used to suppress global error handling */
export const NO_ERROR_HANDLING_HDR = 'X-No-Global-Error-Handling';

/** "Special" name of node containing client applications. */
export const CLIENT_NODE_NAME = '__ClientApplications';

/** Sort callback for node names, putting 'master' in the first place */
export const sortNodesMasterFirst = (a, b) => {
  if (a === 'master') {
    return -1;
  } else if (b === 'master') {
    return 1;
  } else {
    return a.toLocaleLowerCase().localeCompare(b.toLocaleLowerCase());
  }
};

/**
 * Merges two arrays trying to keep ordering as intact as possible by inserting elements relative to its siblings if it is not contained in the other array.
 *
 * See https://stackoverflow.com/questions/53720910/merge-arrays-and-keep-ordering
 */
export function mergeOrdererd<T>(a: T[], b: T[], key: (ele) => any): T[] {
  const result = [];
  [a, b].forEach((array) => {
    array.forEach((item, idx) => {
      // check if the item has already been added, if not, try to add
      if (!result.find((x) => key(x) === key(item))) {
        // if item is not first item, find position of his left sibling in result array
        if (idx) {
          const result_idx = result.indexOf(array[idx - 1]);
          // add item after left sibling position
          result.splice(result_idx + 1, 0, item);
          return;
        }
        result.push(item);
      }
    });
  });
  return result;
}
