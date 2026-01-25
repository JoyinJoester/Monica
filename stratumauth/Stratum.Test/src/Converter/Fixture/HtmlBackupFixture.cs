// Copyright (C) 2025 jmh
// SPDX-License-Identifier: GPL-3.0-only

using System.IO;

namespace Stratum.Test.Converter.Fixture
{
    public class HtmlBackupFixture
    {
        public HtmlBackupFixture()
        {
            var path = Path.Join("data", "backup.html");
            Data = File.ReadAllBytes(path);
        }

        public byte[] Data { get; }
    }
}