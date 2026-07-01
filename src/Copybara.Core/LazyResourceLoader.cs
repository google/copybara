/*
 * Copyright (C) 2017 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

using Copybara.Common;

// 'Console' collides with System.Console; qualify the domain Console interface.
using Console = Copybara.Util.Console.Console;

namespace Copybara;

/// <summary>
/// Load a resource (repository, API client...) lazily to avoid side effects.
/// </summary>
public interface LazyResourceLoader<T> where T : class
{
    /// <summary>
    /// Load the resource.
    /// </summary>
    T Load(Console? console);
}

/// <summary>
/// Factory helpers for <see cref="LazyResourceLoader{T}"/>.
/// </summary>
public static class LazyResourceLoader
{
    /// <summary>
    /// Constructs a <see cref="LazyResourceLoader{T}"/> object that defers the loading of the
    /// resource until <see cref="LazyResourceLoader{T}.Load"/> is called and after that always
    /// returns the same instance.
    /// </summary>
    public static LazyResourceLoader<T> Memoized<T>(LazyResourceLoader<T> @delegate) where T : class
    {
        return new MemoizedLoader<T>(@delegate);
    }

    /// <summary>
    /// Constructs a memoized <see cref="LazyResourceLoader{T}"/> from a delegate function.
    /// </summary>
    public static LazyResourceLoader<T> Memoized<T>(Func<Console?, T> loader) where T : class
    {
        return new MemoizedLoader<T>(new FuncLoader<T>(loader));
    }

    private sealed class FuncLoader<T> : LazyResourceLoader<T> where T : class
    {
        private readonly Func<Console?, T> _loader;

        public FuncLoader(Func<Console?, T> loader) => _loader = loader;

        public T Load(Console? console) => _loader(console);
    }

    private sealed class MemoizedLoader<T> : LazyResourceLoader<T> where T : class
    {
        private readonly LazyResourceLoader<T> _delegate;
        private T? _resource;

        public MemoizedLoader(LazyResourceLoader<T> @delegate) => _delegate = @delegate;

        public T Load(Console? console)
        {
            return _resource ??= Preconditions.CheckNotNull(_delegate.Load(console));
        }
    }
}
