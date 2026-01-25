// Copyright (C) 2025 jmh
// SPDX-License-Identifier: GPL-3.0-only

using System;
using AndroidX.Wear.Widget;

namespace Stratum.WearOS.Callback
{
    public class SwipeDismissCallback : SwipeDismissFrameLayout.Callback
    {
        public event EventHandler Dismiss;
        
        public override void OnDismissed(SwipeDismissFrameLayout layout)
        {
            base.OnDismissed(layout);
            Dismiss?.Invoke(this, EventArgs.Empty);
        }
    }
}