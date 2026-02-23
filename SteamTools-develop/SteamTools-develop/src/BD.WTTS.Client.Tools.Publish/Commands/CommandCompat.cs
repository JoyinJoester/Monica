namespace BD.WTTS.Client.Tools.Publish.Commands;

static partial class CommandCompat
{
    public static Option<T> GetOption<T>(string name, string? desc = null)
    {
        var o = new Option<T>(name)
        {
            Description = desc,
        };
        return o;
    }

    public static Option<T> GetOption<T>(string name, Func<T> defaultValueFactory, string? desc = null)
    {
        var o = new Option<T>(name)
        {
            Description = desc,
            DefaultValueFactory = _ => defaultValueFactory(),
        };
        return o;
    }

    public static void SetHandler(this Command command, Delegate @delegate, params Option[] options)
    {
        command.SetAction(parseResult =>
        {
            var values = options.Select(opt =>
            {
                var typeOpt = opt.GetType();
                var typeArg = typeOpt.GetGenericArguments()[0];
                var methodsGetValue = typeof(ParseResult).GetMethods(BindingFlags.Instance | BindingFlags.Public).Where(x =>
                    x.Name == nameof(ParseResult.GetValue)
                    && x.GetParameters().FirstOrDefault()?.ParameterType.ToString() == typeof(Option<>).ToString())
                .ToArray();
                ArgumentNullException.ThrowIfNull(methodsGetValue);
                var methodGetValue = methodsGetValue.Single().MakeGenericMethod(typeArg);
                var arg = methodGetValue.Invoke(parseResult, [opt]);
                return arg;
            }).ToArray();
            var result = @delegate.DynamicInvoke(values);
            if (result is int code)
            {
                return code;
            }
            return 0;
        });
    }
}
