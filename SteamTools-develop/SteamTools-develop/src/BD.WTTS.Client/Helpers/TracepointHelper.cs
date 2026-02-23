using BD.AppCenter.Analytics;

namespace BD.WTTS.Helpers;

/// <summary>
/// 描点助手类
/// </summary>
static partial class TracepointHelper
{
    /// <summary>
    /// 跟踪带有名称和可选属性的自定义事件
    /// 名称参数不能为空或空值。最大允许长度 = 256
    /// 属性参数最大项数 = 20
    /// 属性键/名称不能为空，最大允许键长度 = 125
    /// 属性值不能为空，允许的最大值长度 = 125
    /// https://learn.microsoft.com/zh-cn/appcenter/sdk/analytics/windows#custom-events
    /// 可以使用 最多 20 个属性 跟踪自己的自定义事件，以了解用户与应用之间的交互
    /// 启动 SDK 后，使用 TrackEvent() 方法通过属性跟踪事件。 最多可以发送 200 个不同的事件名称。 此外，每个事件名称的最大限制为 256 个字符，每个事件属性名称和事件属性值的最大限制为 125 个字符。
    /// </summary>
    /// <param name="name">事件名称（可定义枚举值，最多不超过 200 个名称）</param>
    /// <param name="properties">可选属性</param>
    public static void TrackEvent(string name, IDictionary<string, string>? properties = null)
    {
        Analytics.TrackEvent(name, properties!);
    }
}
