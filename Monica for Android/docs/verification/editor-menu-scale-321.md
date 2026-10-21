# Editor menu interface scaling — 1.0.321

Date: 2026-10-21. Ordinary Android and F-Droid, unreleased 1.0.321.

## Problem and change

The editor used a separate Android popup that restored system density. Monica's interface scale provider changes the page's Compose density, so a menu remained 820px wide even when the page was reduced to 80%. A regression test reproduced this exact failure before the production change (expected 656px, actual 820px).

The shared EditorActionMenu now uses Material's DropdownMenuPopup for positioning and opening/closing animation. It restores the caller's density, context and configuration around the entire menu surface. Width, height, corners, padding, icons and text therefore use the same density as the page. Available physical height is converted to caller units and long content scrolls. The continuous rounded surface and existing actions are retained. All create/edit callers of this component receive the correction, including credential generator and URL menus.

## Validation

- Both editions built production and instrumented test APKs successfully with the test x86_64 ABI enabled.
- EditorActionMenuScaleTest: 2 tests passed per edition, zero failures or skips. The same real interface scale provider used by MainActivity wraps the menu. The test changes 100% → 80% → 120% → 100% while the popup stays open, asserts actual pixel width, height and text ratios, then increases the platform fontScale input to 1.5 and checks that text grows. It also checks action callback/dismissal, a 20-action scrollable menu at 120% and fontScale 1.5, and Back dismissal without another callback.
- Native menu captures at 80%, 100% and 120% were inspected. Pixel dimensions are recorded in the companion JSON. Captures and the editable local M3E Canvas draft remain under ignored local design storage; no screenshots are committed.
- Both editions have byte-identical production component and regression test sources. The companion JSON includes their hashes and the APK hashes checked against installed packages.
- Reused the already-running public API 32 x86_64 AVD with isolated user 10. Restored user 0, input method, Autofill, accessibility, screen timeout and the prior lockscreen setting. The previously running AVD remains running.

Local M3E Canvas project: editor-menu-scale-321-20261021. The logical 80/100/120% draft is a design reference; native pixel measurements establish actual same-device scaling.

Validation is on the shared API 32 emulator. Physical-device and other API-level rendering are not covered by this run.
