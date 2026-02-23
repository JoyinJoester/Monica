// Common\src\BD.Common8.Bcl\Extensions\StreamExtensions.cs

namespace System.Extensions;

/// <summary>
/// 提供对 <see cref="Stream"/> 类型的扩展函数
/// </summary>
internal static partial class StreamExtensions
{
    [MethodImpl(MethodImplOptions.AggressiveInlining)]
    public static int ReadInt32(this Stream stream)
    {
        Span<byte> data = stackalloc byte[sizeof(int)];
        stream.ReadExactly(data);
        return BitConverter.ToInt32(data);
    }

    [MethodImpl(MethodImplOptions.AggressiveInlining)]
    public static ushort ReadUInt16(this Stream stream)
    {
        Span<byte> data = stackalloc byte[sizeof(ushort)];
        stream.ReadExactly(data);
        return BitConverter.ToUInt16(data);
    }

    [MethodImpl(MethodImplOptions.AggressiveInlining)]
    public static uint ReadUInt32(this Stream stream)
    {
        Span<byte> data = stackalloc byte[sizeof(uint)];
        stream.ReadExactly(data);
        return BitConverter.ToUInt32(data);
    }

    [MethodImpl(MethodImplOptions.AggressiveInlining)]
    public static void WriteUInt32(this Stream stream, uint value)
    {
        Span<byte> data = stackalloc byte[sizeof(uint)];
        BitConverter.TryWriteBytes(data, value);
        stream.Write(data);
    }
}