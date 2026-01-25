// Copyright (C) 2025 jmh
// SPDX-License-Identifier: GPL-3.0-only

using System;
using System.Linq;
using System.Text;
using System.Threading.Tasks;
using HtmlAgilityPack;
using Stratum.Core.Backup;

namespace Stratum.Core.Converter
{
    public class HtmlBackupConverter : UriListBackupConverter
    {
        public HtmlBackupConverter(IIconResolver iconResolver) : base(iconResolver)
        {
        }

        public override Task<ConversionResult> ConvertAsync(byte[] data, string password = null)
        {
            var html = Encoding.UTF8.GetString(data);
            var document = new HtmlDocument();
            document.LoadHtml(html);
            
            var nodes = document.DocumentNode.SelectNodes("//code");

            if (nodes == null)
            {
                throw new ArgumentException("No URIs found in file");
            }

            return Task.FromResult(ConvertLines(nodes.Select(n => n.InnerText)));
        }
    }
}