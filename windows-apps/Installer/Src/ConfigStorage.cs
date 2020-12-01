﻿using Bdeploy.Shared;
using System;
using System.Diagnostics;
using System.IO;
using System.Reflection;
using System.Runtime.Serialization.Json;
using System.Text;
using System.Text.RegularExpressions;
using System.Windows;

namespace Bdeploy.Installer.Models {
    /// <summary>
    /// Knows how to read and write the embedded configuration. 
    /// 
    /// The configuration has a special format where the acutal data is surrounded by start
    /// and end tags. Layout:
    /// <!--
    ///     START_BDEPLOY
    ///       START_CONFIG
    ///        CONFIG
    ///       END_CONFIG
    ///       RANDOM_DATA
    ///     END_BDEPLOY
    /// -->
    /// 
    /// It is expected that this configuration is embedded somewhere in the executable.
    /// 
    /// </summary>
    public class ConfigStorage {

        public static readonly string START_MARKER = "###START_BDEPLOY###";
        public static readonly string START_CONFIG = "###START_CONFIG###";
        public static readonly string END_CONFIG = "###END_CONFIG###";
        public static readonly string END_MARKER = "###END_BDEPLOY###";

        /// <summary>
        /// Returns the configuration to use. Either the one passed to the application or the embedded is used.
        /// </summary>
        public static Config GetConfig(StartupEventArgs e) {
            if (e.Args.Length == 1 && File.Exists(e.Args[0])) {
                return ConfigStorage.ReadConfigurationFromFile(e.Args[0]);
            }
            return ConfigStorage.ReadEmbeddedConfiguration();
        }

        /// <summary>
        /// De-Serializes the configuration from the given file.
        /// </summary>
        /// <returns></returns>
        public static Config ReadConfigurationFromFile(string file) {
            string payload = File.ReadAllText(file);
            return DeserializeConfig(payload);
        }

        /// <summary>
        /// De-Serializes the embedded configuration 
        /// </summary>
        /// <returns></returns>
        public static Config ReadEmbeddedConfiguration() {
            string payload = ReadEmbeddedConfig();
            return DeserializeConfig(payload);
        }

        /// <summary>
        /// Parses the payload in order to extract the deserialze the configuration object.
        /// </summary>
        private static Config DeserializeConfig(string payload) {
            string regex = string.Format("{0}.*{1}(.*){2}.*{3}", START_MARKER, START_CONFIG, END_CONFIG, END_MARKER);
            Regex rx = new Regex(regex, RegexOptions.Singleline | RegexOptions.IgnoreCase);
            Match match = rx.Match(payload);
            if (!match.Success) {
                return null;
            }
            string value = match.Groups[1].Value.Trim();
            UTF8Encoding encoding = new UTF8Encoding(false);
            DataContractJsonSerializer ser = new DataContractJsonSerializer(typeof(Config));
            Config config = (Config)ser.ReadObject(new MemoryStream(encoding.GetBytes(value)));
            return config;
        }

        /// <summary>
        /// Serializes the configururation to the given file. 
        /// </summary>
        public static void WriteConfiguration(string file, Config config) {
            var encoding = new UTF8Encoding(false);
            using (StreamWriter writer = new StreamWriter(new FileStream(file, FileMode.Create), encoding)) {
                // Write header of file
                writer.Write(START_MARKER);

                // Write JSON 
                writer.Write(START_CONFIG);
                writer.Flush();
                DataContractJsonSerializer ser = new DataContractJsonSerializer(typeof(Config));
                ser.WriteObject(writer.BaseStream, config);
                writer.Flush();
                writer.Write(END_CONFIG);

                // Write dummy-data at the end until the desired size is reached
                writer.Flush();
                long remaingBytes = writer.BaseStream.Length - END_MARKER.Length;
                for (long i = 0; i < remaingBytes; i++) {
                    writer.Write('0');
                }
                writer.Write(END_MARKER);
            }
        }

        /// <summary>
        /// Reads the embedded configuration and returns the payload as string
        /// </summary>
        /// <returns></returns>
        public static string ReadEmbeddedConfig() {
            var fileName = Process.GetCurrentProcess().MainModule.FileName;

            string config = "";
            bool markerDetected = false;
            foreach (string line in File.ReadLines(fileName)) {
                if (line.Contains(START_MARKER)) {
                    markerDetected = true;
                }
                if (markerDetected) {
                    config += line;
                }
                if (line.Contains(END_MARKER)) {
                    return config;
                }
            }
            return config;
        }
    }
}