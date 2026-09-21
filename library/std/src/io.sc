/// Visible authority to interact with the native host environment.
///
/// The native entry boundary is the only implicit handler for this exact
/// standard identity. Importing this module grants no authority by itself.
pub let io = effect {}

/// Portable classifications for recoverable synchronous host failures.
pub let IoErrorKind = enum {
  NotFound,
  PermissionDenied,
  AlreadyExists,
  InvalidInput,
  InvalidData,
  Interrupted,
  WouldBlock,
  WriteZero,
  UnexpectedEof,
  BrokenPipe,
  Unsupported,
  OutOfMemory,
  Other,
}

extend<IoErrorKind, core.marker.Copyable> {}

/// A portable failure classification plus an optional signed host code.
///
/// Portable control flow must inspect `kind`; `raw_code` is diagnostic data
/// and is never interpreted as a stable cross-platform value.
pub let IoError = struct {
  failure: IoErrorKind,
  host_code: core.Option<i32>,
}

/// Lossless storage for one process argument.
pub let ProcessArgument = struct { bytes: alloc.vec.Vec<u8> }

extend<ProcessArgument> {
  let into_bytes(move self)(): alloc.vec.Vec<u8> = {  self.bytes }
}

extend<IoError> {
  let kind(self: Borrow<self>)(): IoErrorKind = {  self.failure }
  let raw_code<r: region>
    (self: Borrow<r><self>)
    (): Borrow<r><core.Option<i32>> = {
    borrow(self.host_code)
  }
}

// These bridges are private, fixed-shape runtime contracts. Their C
// implementations are emitted by the native backend and their names are
// reserved from user foreign declarations.
let host_read(
  descriptor: i32,
  data: Ptr<mut><u8>,
  length: u64,
  failure: Ptr<mut><i32>,
  raw_code: Ptr<mut><i32>,
): i64 = foreign<c, "sali_host_read">

let host_write(
  descriptor: i32,
  data: Ptr<u8>,
  length: u64,
  failure: Ptr<mut><i32>,
  raw_code: Ptr<mut><i32>,
): i64 = foreign<c, "sali_host_write">

let host_argument_count(): u64 = foreign<c, "sali_host_argument_count">
  let host_argument_length(index: u64): u64 = foreign<c, "sali_host_argument_length">
  let host_argument_byte(index: u64, offset: u64): u8 = foreign<c, "sali_host_argument_byte">
  let host_open(
  path: Ptr<u8>,
  flags: i32,
  failure: Ptr<mut><i32>,
  raw_code: Ptr<mut><i32>,
): i32 = foreign<c, "sali_host_open">
  let host_close(
  descriptor: i32,
  failure: Ptr<mut><i32>,
  raw_code: Ptr<mut><i32>,
): i32 = foreign<c, "sali_host_close">
  let host_flush(
  descriptor: i32,
  failure: Ptr<mut><i32>,
  raw_code: Ptr<mut><i32>,
): i32 = foreign<c, "sali_host_flush">
  let host_seek(
  descriptor: i32,
  offset: i64,
  origin: i32,
  failure: Ptr<mut><i32>,
  raw_code: Ptr<mut><i32>,
): i64 = foreign<c, "sali_host_seek">

let decode_error_kind(value: i32): IoErrorKind = {
  if(value == 0) { NotFound }
  else: {
    if(value == 1) { PermissionDenied }
    else: {
      if(value == 2) { AlreadyExists }
      else: {
        if(value == 3) { InvalidInput }
        else: {
          if(value == 4) { InvalidData }
          else: {
            if(value == 5) { Interrupted }
            else: {
              if(value == 6) { WouldBlock }
              else: {
                if(value == 7) { WriteZero }
                else: {
                  if(value == 8) { UnexpectedEof }
                  else: {
                    if(value == 9) { BrokenPipe }
                    else: {
                      if(value == 10) { Unsupported }
                      else: {
                        if(value == 11) { OutOfMemory }
                        else: { Other }
                      }
                    }
                  }
                }
              }
            }
          }
        }
      }
    }
  }
}

let host_error(failure: i32, raw_code: i32): IoError = {
  IoError { failure: decode_error_kind(failure), host_code: core.Option.Some(raw_code) }
}

let generated_error(failure: IoErrorKind): IoError = {
  IoError { failure: failure, host_code: core.Option.None }
}

let count_result(
  count: i64,
  failure: i32,
  raw_code: i32,
): core.Result<IoError><u64> = {
  if(count < 0) {
    core.Result.Err(host_error(failure, raw_code))
  } else: {
    match(count.checked_into<Output: u64>()) {
      Some(value) => core.Result.Ok(value),
      None => core.Result.Err(generated_error(InvalidData)),
    }
  }
}

/// Performs one synchronous read Attempt from standard input.
///
/// A successful zero count on a non-empty buffer is EOF. Short reads are
/// successful and `Interrupted` is preserved.
pub let read_stdin with<io>
  (buffer: Borrow<mut><core.memory.Slice<u8>>): core.Result<IoError><u64> = {
  read_stream_at(0)(buffer)(0)
}

let read_stream_at with<io>
  (descriptor: i32)
  (buffer: Borrow<mut><core.memory.Slice<u8>>)
  (offset: u64): core.Result<IoError><u64> = {
  let length = buffer.len<mut>()
  if(offset >= length) {
    return(core.Result.Ok(0))
  }
  let mut failure: i32 = 12
  let mut raw_code: i32 = 0
  let count = unsafe {
    let data = raw_offset(raw_slice_ptr<mut>(buffer), offset)
    host_read(
      descriptor,
      data,
      length - offset,
      ptr<mut>(borrow<mut>(failure)),
      ptr<mut>(borrow<mut>(raw_code)),
    )
  }
  count_result(count, failure, raw_code)
}

/// Performs one synchronous write Attempt to standard output.
pub let write_stdout with<io>
  (bytes: Borrow<core.memory.Slice<u8>>): core.Result<IoError><u64> = {
  write_stream_at(1)(bytes)(0)
}

/// Performs one synchronous write Attempt to standard error.
pub let write_stderr with<io>
  (bytes: Borrow<core.memory.Slice<u8>>): core.Result<IoError><u64> = {
  write_stream_at(2)(bytes)(0)
}

let write_stream_at with<io>
  (descriptor: i32)
  (bytes: Borrow<core.memory.Slice<u8>>)
  (offset: u64): core.Result<IoError><u64> = {
  let length = bytes.len()
  if(offset >= length) {
    return(core.Result.Ok(0))
  }
  let mut failure: i32 = 12
  let mut raw_code: i32 = 0
  let count = unsafe {
    let data = raw_offset(raw_slice_ptr(bytes), offset)
    host_write(
      descriptor,
      data,
      length - offset,
      ptr<mut>(borrow<mut>(failure)),
      ptr<mut>(borrow<mut>(raw_code)),
    )
  }
  count_result(count, failure, raw_code)
}

let write_all_stream with<io>
  (descriptor: i32)
  (bytes: Borrow<core.memory.Slice<u8>>): core.Result<IoError><()> = {
  let length = bytes.len()
  let mut written: u64 = 0
  while(written < length) {
    match(write_stream_at(descriptor)(bytes)(written)) {
      Ok(0) => return(core.Result.Err(generated_error(WriteZero))),
      Ok(count) => written = written + count,
      Err(error) => do {
        match(error.kind()) {
          Interrupted => (),
          _ => return(core.Result.Err(error)),
        }
      },
    }
  }
  core.Result.Ok(())
}

/// Writes every byte to standard output, retrying interruption.
pub let write_stdout_all with<io>
  (bytes: Borrow<core.memory.Slice<u8>>): core.Result<IoError><()> = {
  write_all_stream(1)(bytes)
}

/// Writes every byte to standard error, retrying interruption.
pub let write_stderr_all with<io>
  (bytes: Borrow<core.memory.Slice<u8>>): core.Result<IoError><()> = {
  write_all_stream(2)(bytes)
}

/// Direct descriptor writes are unbuffered, so flushing is a successful
/// synchronization point without another host call.
pub let flush_stdout with<io>(): core.Result<IoError><()> = {  core.Result.Ok(()) }
pub let flush_stderr with<io>(): core.Result<IoError><()> = {  core.Result.Ok(()) }

/// Writes validated UTF-8 text to standard output.
pub let print with<io>
  (value: Borrow<core.string.str>): core.Result<IoError><()> = {
  let bytes = value.as_bytes()
  write_stdout_all(bytes)
}

/// Writes validated UTF-8 text followed by one LF byte.
pub let println with<io>
  (value: Borrow<core.string.str>): core.Result<IoError><()> = {
  match(print(value)) {
    Err(error) => core.Result.Err(error),
    Ok(_) => do {
      let newline: Array<u8><1> = [10]
      let bytes = newline.as_slice()
      write_stdout_all(bytes)
    },
  }
}

/// Writes validated UTF-8 text to standard error.
pub let eprint with<io>
  (value: Borrow<core.string.str>): core.Result<IoError><()> = {
  let bytes = value.as_bytes()
  write_stderr_all(bytes)
}

/// Writes validated UTF-8 text followed by one LF byte to standard error.
pub let eprintln with<io>
  (value: Borrow<core.string.str>): core.Result<IoError><()> = {
  match(eprint(value)) {
    Err(error) => core.Result.Err(error),
    Ok(_) => do {
      let newline: Array<u8><1> = [10]
      let bytes = newline.as_slice()
      write_stderr_all(bytes)
    },
  }
}

/// Reads exactly `buffer.len()` bytes or reports `UnexpectedEof`.
pub let read_stdin_exact with<io>
  (buffer: Borrow<mut><core.memory.Slice<u8>>): core.Result<IoError><()> = {
  let length = buffer.len<mut>()
  let mut read: u64 = 0
  while(read < length) {
    match(read_stream_at(0)(buffer)(read)) {
      Ok(0) => return(core.Result.Err(generated_error(UnexpectedEof))),
      Ok(count) => read = read + count,
      Err(error) => do {
        match(error.kind()) {
          Interrupted => (),
          _ => return(core.Result.Err(error)),
        }
      },
    }
  }
  core.Result.Ok(())
}

/// Reads one UTF-8 line including its LF terminator. EOF before any byte is
/// `None`; invalid UTF-8 is `InvalidData`.
pub let read_line with<io>(): core.Result<IoError><core.Option<String>> = {
  let mut bytes = alloc.vec.Vec<u8>.new()
  let mut done = false
  while(!done) {
    let mut byte: Array<u8><1> = [0]
    let outcome = do {
      let buffer = byte.as_slice<mut>()
      read_stdin(buffer)
    }
    match(outcome) {
      Ok(0) => done = true,
      Ok(_) => do {
        let value = byte[0]
        bytes.push(value)
        if(value == 10) { done = true }
      },
      Err(error) => do {
        match(error.kind()) {
          Interrupted => (),
          _ => return(core.Result.Err(error)),
        }
      },
    }
  }
  if(bytes.is_empty()) {
    core.Result.Ok(core.Option.None)
  } else: {
    match(alloc.string.string_from_utf8(bytes)) {
      Ok(text) => core.Result.Ok(core.Option.Some(text)),
      Err(_) => core.Result.Err(generated_error(InvalidData)),
    }
  }
}

/// Returns the number of process arguments, including the executable name.
pub let argument_count with<io>
  (): u64 = {
  unsafe { host_argument_count() }
}

/// Copies one process argument losslessly as Unix bytes.
pub let argument_bytes with<io>
  (index: u64): core.Option<alloc.vec.Vec<u8>> = {
  let count = argument_count()
  if(index >= count) {
    core.Option.None
  } else: {
    let length = unsafe { host_argument_length(index) }
    let mut bytes = alloc.vec.Vec<u8>.with_capacity(length)
    let mut offset: u64 = 0
    while(offset < length) {
      bytes.push(unsafe { host_argument_byte(index, offset) })
      offset = offset + 1
    }
    core.Option.Some(bytes)
  }
}

/// Copies all process arguments losslessly as Unix bytes.
pub let arguments_bytes with<io>
  (): alloc.vec.Vec<ProcessArgument> = {
  let count = argument_count()
  let mut arguments = alloc.vec.Vec<ProcessArgument>.with_capacity(count)
  let mut index: u64 = 0
  while(index < count) {
    match(argument_bytes(index)) {
      Some(argument) => do { arguments.push(ProcessArgument { bytes: argument }) },
      None => (),
    }
    index = index + 1
  }
  arguments
}

/// Copies all process arguments as validated UTF-8 text.
///
/// The first invalid host argument returns `InvalidData`; no replacement
/// decoding is performed.
pub let arguments with<io>(): core.Result<IoError><alloc.vec.Vec<String>> = {
  let mut bytes = arguments_bytes()
  let count = bytes.len()
  let mut text = alloc.vec.Vec<String>.with_capacity(count)
  let mut index: u64 = 0
  while(index < count) {
    let argument = bytes.remove(0).into_bytes()
    match(alloc.string.string_from_utf8(argument)) {
      Ok(value) => text.push(value),
      Err(_) => return(core.Result.Err(generated_error(InvalidData))),
    }
    index = index + 1
  }
  core.Result.Ok(text)
}

/// Portable origins for File seeks.
pub let SeekFrom = enum { Start, Current, End }
extend<SeekFrom, core.marker.Copyable> {}

/// Validated options for opening one native File.
pub let OpenOptions = struct {
  read: bool,
  write: bool,
  append: bool,
  truncate: bool,
  create: bool,
  create_new: bool,
}
extend<OpenOptions, core.marker.Copyable> {}

extend<OpenOptions> {
  let read_only(): OpenOptions = {
    OpenOptions { read: true, write: false, append: false, truncate: false, create: false, create_new: false }
  }
  let write_truncate(): OpenOptions = {
    OpenOptions { read: false, write: true, append: false, truncate: true, create: true, create_new: false }
  }
  let append_only(): OpenOptions = {
    OpenOptions { read: false, write: true, append: true, truncate: false, create: true, create_new: false }
  }
  let read_write(): OpenOptions = {
    OpenOptions { read: true, write: true, append: false, truncate: false, create: false, create_new: false }
  }
  let create_new(): OpenOptions = {
    OpenOptions { read: false, write: true, append: false, truncate: false, create: true, create_new: true }
  }
  let with_read(move self)
    (enabled: bool): OpenOptions = {
    OpenOptions { read: enabled, write: self.write, append: self.append, truncate: self.truncate, create: self.create, create_new: self.create_new }
  }
  let with_write(move self)
    (enabled: bool): OpenOptions = {
    OpenOptions { read: self.read, write: enabled, append: self.append, truncate: self.truncate, create: self.create, create_new: self.create_new }
  }
  let with_append(move self)
    (enabled: bool): OpenOptions = {
    OpenOptions { read: self.read, write: self.write, append: enabled, truncate: self.truncate, create: self.create, create_new: self.create_new }
  }
  let with_truncate(move self)
    (enabled: bool): OpenOptions = {
    OpenOptions { read: self.read, write: self.write, append: self.append, truncate: enabled, create: self.create, create_new: self.create_new }
  }
  let with_create(move self)
    (enabled: bool): OpenOptions = {
    OpenOptions { read: self.read, write: self.write, append: self.append, truncate: self.truncate, create: enabled, create_new: self.create_new }
  }
  let with_create_new(move self)
    (enabled: bool): OpenOptions = {
    OpenOptions { read: self.read, write: self.write, append: self.append, truncate: self.truncate, create: self.create, create_new: enabled }
  }
}

/// Unique owner of one native File descriptor.
pub let File = struct { descriptor: Ptr<mut><i32> }

let option_flags(options: OpenOptions): core.Result<IoError><i32> = {
  if(!options.read && !options.write) {
    return(core.Result.Err(generated_error(InvalidInput)))
  }
  if((options.append || options.truncate || options.create || options.create_new) &&
    !options.write) {
    return(core.Result.Err(generated_error(InvalidInput)))
  }
  if(options.create_new && !options.create) {
    return(core.Result.Err(generated_error(InvalidInput)))
  }
  if(options.append && options.truncate) {
    return(core.Result.Err(generated_error(InvalidInput)))
  }
  let mut flags: i32 = 0
  if(options.write && !options.read) { flags = flags + 1 }
  if(options.read && options.write) { flags = flags + 2 }
  if(options.create) { flags = flags + 4 }
  if(options.create_new) { flags = flags + 8 }
  if(options.truncate) { flags = flags + 16 }
  if(options.append) { flags = flags + 32 }
  core.Result.Ok(flags)
}

let descriptor(value: Borrow<File>): i32 = {
  unsafe { *value.descriptor }
}

let close_descriptor with<io>
  (value: Ptr<mut><i32>): core.Result<IoError><()> = {
  let descriptor = unsafe { *value }
  if(descriptor < 0) { return(core.Result.Ok(())) }
  unsafe { *value = -1 }
  let mut failure: i32 = 12
  let mut raw_code: i32 = 0
  let status = unsafe {
    host_close(
      descriptor,
      ptr<mut>(borrow<mut>(failure)),
      ptr<mut>(borrow<mut>(raw_code)),
    )
  }
  if(status == 0) {
    core.Result.Ok(())
  } else: {
    core.Result.Err(host_error(failure, raw_code))
  }
}

/// Opens a UTF-8 path without normalization. Embedded NUL is rejected.
pub let open with<io>
  (path: Borrow<core.string.str>)
  (options: OpenOptions): core.Result<IoError><File> = {
  let flags = match(option_flags(options)) {
    Ok(value) => value,
    Err(error) => return(core.Result.Err(error)),
  }
  let source = path.as_bytes()
  let length = source.len()
  let mut bytes = alloc.vec.Vec<u8>.with_capacity(length + 1)
  let mut index: u64 = 0
  while(index < length) {
    let byte = unsafe { *raw_offset(raw_slice_ptr(source), index) }
    if(byte == 0) { return(core.Result.Err(generated_error(InvalidInput))) }
    bytes.push(byte)
    index = index + 1
  }
  bytes.push(0)
  let view = bytes.as_slice()
  let mut failure: i32 = 12
  let mut raw_code: i32 = 0
  let descriptor = unsafe {
    host_open(
      raw_slice_ptr(view),
      flags,
      ptr<mut>(borrow<mut>(failure)),
      ptr<mut>(borrow<mut>(raw_code)),
    )
  }
  if(descriptor < 0) {
    core.Result.Err(host_error(failure, raw_code))
  } else: {
    let owner = alloc.boxed.Box<i32>.new(descriptor)
    core.Result.Ok(File { descriptor: owner.into_raw() })
  }
}

extend<File> {
  let read with<io>
    (self: Borrow<mut><self>)
    (buffer: Borrow<mut><core.memory.Slice<u8>>): core.Result<IoError><u64> = {
    read_stream_at(descriptor(self))(buffer)(0)
  }

  let read_exact with<io>
    (self: Borrow<mut><self>)
    (buffer: Borrow<mut><core.memory.Slice<u8>>): core.Result<IoError><()> = {
    let length = buffer.len<mut>()
    let mut read: u64 = 0
    while(read < length) {
      match(read_stream_at(descriptor(self))(buffer)(read)) {
        Ok(0) => return(core.Result.Err(generated_error(UnexpectedEof))),
        Ok(count) => read = read + count,
        Err(error) => do {
          match(error.kind()) {
            Interrupted => (),
            _ => return(core.Result.Err(error)),
          }
        },
      }
    }
    core.Result.Ok(())
  }

  let write with<io>
    (self: Borrow<self>)
    (bytes: Borrow<core.memory.Slice<u8>>): core.Result<IoError><u64> = {
    write_stream_at(descriptor(self))(bytes)(0)
  }

  let write_all with<io>
    (self: Borrow<self>)
    (bytes: Borrow<core.memory.Slice<u8>>): core.Result<IoError><()> = {
    write_all_stream(descriptor(self))(bytes)
  }

  let flush with<io>
    (self: Borrow<self>)
    (): core.Result<IoError><()> = {
    let mut failure: i32 = 12
    let mut raw_code: i32 = 0
    let status = unsafe {
      host_flush(
        descriptor(self),
        ptr<mut>(borrow<mut>(failure)),
        ptr<mut>(borrow<mut>(raw_code)),
      )
    }
    if(status == 0) {
      core.Result.Ok(())
    } else: {
      core.Result.Err(host_error(failure, raw_code))
    }
  }

  let seek with<io>
    (self: Borrow<self>)
    (offset: i64, origin: SeekFrom): core.Result<IoError><u64> = {
    let native_origin = match(origin) {
      Start => 0,
      Current => 1,
      End => 2,
    }
    let mut failure: i32 = 12
    let mut raw_code: i32 = 0
    let position = unsafe {
      host_seek(
        descriptor(self),
        offset,
        native_origin,
        ptr<mut>(borrow<mut>(failure)),
        ptr<mut>(borrow<mut>(raw_code)),
      )
    }
    if(position < 0) {
      core.Result.Err(host_error(failure, raw_code))
    } else: {
      match(position.checked_into<Output: u64>()) {
        Some(value) => core.Result.Ok(value),
        None => core.Result.Err(generated_error(InvalidData)),
      }
    }
  }

  /// Consumes the owner. The descriptor is invalidated before the one close
  /// Attempt, including when close reports an error.
  let close with<io>
    (move self)
    (): core.Result<IoError><()> = {
    close_descriptor(self.descriptor)
  }
}

extend<File, core.marker.Droppable> {
  let drop(self: Borrow<mut><self>)
    (): () = {
    let descriptor = unsafe { *self.descriptor }
    if(descriptor >= 0) {
      unsafe { *self.descriptor = -1 }
      let mut failure: i32 = 12
      let mut raw_code: i32 = 0
      let status = unsafe {
        host_close(
          descriptor,
          ptr<mut>(borrow<mut>(failure)),
          ptr<mut>(borrow<mut>(raw_code)),
        )
      }
    }
    let owner = unsafe { alloc.boxed.Box<i32>.from_raw(self.descriptor) }
  }
}

/// Reads a whole File while enforcing a caller-selected byte limit.
pub let read_file with<io>
  (path: Borrow<core.string.str>)
  (limit: u64): core.Result<IoError><alloc.vec.Vec<u8>> = {
  let mut input = match(open(path)(OpenOptions.read_only())) {
    Ok(value) => value,
    Err(error) => return(core.Result.Err(error)),
  }
  let mut output = alloc.vec.Vec<u8>.new()
  let mut done = false
  while(!done) {
    let mut chunk = alloc.vec.Vec<u8>.with_capacity(4096)
    let mut initialized: u64 = 0
    while(initialized < 4096) {
      chunk.push(0)
      initialized = initialized + 1
    }
    let outcome = do {
      let buffer = chunk.as_slice<mut>()
      input.read(buffer)
    }
    match(outcome) {
      Ok(0) => done = true,
      Ok(count) => do {
        if(output.len() > limit || count > limit - output.len()) {
          return(core.Result.Err(generated_error(InvalidData)))
        }
        let mut index: u64 = 0
        while(index < count) {
          output.push(chunk[index])
          index = index + 1
        }
      },
      Err(error) => do {
        match(error.kind()) {
          Interrupted => (),
          _ => return(core.Result.Err(error)),
        }
      },
    }
  }
  core.Result.Ok(output)
}

/// Creates or truncates a File and writes every byte.
pub let write_file with<io>
  (path: Borrow<core.string.str>)
  (bytes: Borrow<core.memory.Slice<u8>>): core.Result<IoError><()> = {
  let output = match(open(path)(OpenOptions.write_truncate())) {
    Ok(value) => value,
    Err(error) => return(core.Result.Err(error)),
  }
  match(output.write_all(bytes)) {
    Err(error) => core.Result.Err(error),
    Ok(_) => output.close(),
  }
}
