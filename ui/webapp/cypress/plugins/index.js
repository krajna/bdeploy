//@ts-check
// ***********************************************************
// This example plugins/index.js can be used to load plugins
//
// You can change the location of this file or turn off loading
// the plugins file with the 'pluginsFile' configuration option.
//
// You can read more here:
// https://on.cypress.io/plugins-guide
// ***********************************************************

// This function is called when a project is opened or re-opened (e.g. due to
// the project's config changing)
const request = require('request');
const fs = require('fs-extra');
const { rmdir, rename } = require('fs');
const AdmZip = require('adm-zip');

module.exports = (on, config) => {
  on('task', {
    downloadFileFromUrl(args) {
      const fileName = args.fileName;
      return new Promise((resolve, reject) => {
        request({ url: args.url, encoding: null, headers: {} }, function (err, res, body) {
          if (!res) {
            return reject(new Error('No response: ' + err));
          }
          if (res.statusCode !== 200) {
            return reject(new Error('Bad status code: ' + res.statusCode));
          }

          fs.outputFileSync(fileName, body);
          resolve(body);
        });
      });
    },

    deleteFolder(folderName) {
      console.log('deleting folder %s', folderName);

      return new Promise((resolve, reject) => {
        rmdir(folderName, { maxRetries: 10, recursive: true }, (err) => {
          if (err) {
            console.error(err);

            return reject(err);
          }
          resolve(null);
        });
      });
    },

    moveFile(args) {
      const from = args.from;
      const to = args.to;
      console.log('moving file %s to %s', from, to);

      return new Promise((resolve, reject) => {
        rename(from, to, (err) => {
          if (err) {
            console.error(err);
            return reject(err);
          }
          resolve(null);
        });
      });
    },

    validateZipFile(filename, expectedEntry) {
      console.log('loading zip', filename);
      const zip = new AdmZip(filename);
      const zipEntries = zip.getEntries();

      const names = zipEntries.map((entry) => entry.entryName).sort();

      console.log('zip file %s has entries %o', filename, names);

      if (expectedEntry && !names.find(expectedEntry)) {
        throw new Error(`Expected Entry ${expectedEntry} not found in ${filename}`);
      }

      return null;
    },
  });

  if (config.env.DISABLE_COVERAGE !== 'yes') {
    // enable code coverage collection
    require('@cypress/code-coverage/task')(on, config);
  }

  // IMPORTANT to return the config object
  // with the any changed environment variables
  return config;
};
