/*
 * Copyright (C) 2024 Google LLC.
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

namespace Copybara.Util;

/// <summary>
/// A <see cref="Stream"/> that limits the output to a certain number of bytes. After that, it skips
/// writing to the delegated output. As an optional last step, it can write an
/// <c>afterLimitSuffix</c> byte sequence (generally text) to express that it has reached the stream
/// limit (e.g. "(Skipped the rest of the output)"). Port of
/// <c>com.google.copybara.util.LimitFilterOutputStream</c> (a <c>FilterOutputStream</c> in Java).
/// </summary>
public class LimitFilterOutputStream : Stream
{
    private readonly Stream _out;
    private int _left;
    private bool _suffixWritten;
    private readonly byte[] _afterLimitSuffix;

    /// <summary>Construct a limited output stream.</summary>
    /// <param name="output">delegate output stream to wrap.</param>
    /// <param name="byteLimit">
    /// number of bytes to write before skipping any further write. Required to be &gt; 0.
    /// </param>
    /// <param name="afterLimitSuffix">
    /// an optional suffix (generally a string) to write after reaching the limit.
    /// </param>
    public LimitFilterOutputStream(Stream output, int byteLimit, byte[] afterLimitSuffix)
    {
        _out = output;
        Preconditions.CheckArgument(byteLimit > 0, "byteLimit is expected to be greater than zero.");
        _left = byteLimit;
        _afterLimitSuffix = afterLimitSuffix;
    }

    public override bool CanRead => false;
    public override bool CanSeek => false;
    public override bool CanWrite => true;
    public override long Length => throw new NotSupportedException();

    public override long Position
    {
        get => throw new NotSupportedException();
        set => throw new NotSupportedException();
    }

    public void Write(int b)
    {
        if (_left > 0)
        {
            _out.WriteByte((byte)b);
            _left--;
        }
        else
        {
            MaybeWriteLimitSuffix();
        }
    }

    public override void Write(byte[] buffer, int offset, int count)
    {
        if (count <= 0)
        {
            return;
        }

        if (_left == 0)
        {
            MaybeWriteLimitSuffix();
            return;
        }

        if (count > _left)
        {
            int oldLeft = _left;
            _left = 0;
            _out.Write(buffer, offset, oldLeft);
            MaybeWriteLimitSuffix();
        }
        else
        {
            _left -= count;
            _out.Write(buffer, offset, count);
        }
    }

    public override void WriteByte(byte value) => Write(value);

    /// <summary>Write at most once the suffix to the stream.</summary>
    private void MaybeWriteLimitSuffix()
    {
        if (_afterLimitSuffix.Length > 0 && !_suffixWritten)
        {
            _out.Write(_afterLimitSuffix, 0, _afterLimitSuffix.Length);
            _suffixWritten = true;
        }
    }

    public override void Flush() => _out.Flush();

    public override int Read(byte[] buffer, int offset, int count) =>
        throw new NotSupportedException();

    public override long Seek(long offset, SeekOrigin origin) => throw new NotSupportedException();

    public override void SetLength(long value) => throw new NotSupportedException();
}
