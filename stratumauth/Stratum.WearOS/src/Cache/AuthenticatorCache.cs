// Copyright (C) 2025 jmh
// SPDX-License-Identifier: GPL-3.0-only

using Android.Content;
using Stratum.Droid.Shared.Wear;

namespace Stratum.WearOS.Cache
{
    public class AuthenticatorCache(Context context) : ListCache<WearAuthenticator>("authenticators", context);
}