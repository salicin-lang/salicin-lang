// A complete effectful batch-processing program over the frozen M0 core. The
// process exits with 42 after applying four valid transactions, or 1 on overdraft.

let Option = core.Option
let Iterator = core.iter.Iterator
let IntoIterator = core.iter.IntoIterator
let OwnedItem = core.iter.OwnedItem

let Transaction = enum {
  Credit(i32),
  Debit(i32),
}

let overdraft = effect {
  reject: (): never
}

let Ledger = struct {
  balance: i32,
  processed: i32,
}

let Account = trait {
  credit: (self: Borrow<mut><self>)(amount: i32): ()
  debit: (self: Borrow<mut><self>)(amount: i32): ()
  snapshot: (self: Borrow<self>)(): i32
}

extend(Ledger, Account) {
  let credit = {
    (self: Borrow<mut><self>)
    (amount: i32): () =>
    self.balance = self.balance + amount
    self.processed = self.processed + 1
  }

  let debit = {
    (self: Borrow<mut><self>)
    (amount: i32): () =>
    self.balance = self.balance - amount
    self.processed = self.processed + 1
  }

  let snapshot = {
    (self: Borrow<self>)
    (): i32 =>
    if(self.processed == 4) { self.balance } else: { 0 }
  }
}

let Batch = struct {
  index: i32,
}

extend(Batch, Iterator) {
  let Item = OwnedItem<Transaction>;

  let next: <r: region> = { (self: Borrow<mut><r><self>)
    (): Option<Transaction> =>
    let transaction: Option<Transaction> = match(self.index) {
      0 => Some(Transaction.Credit(30)),
      1 => Some(Transaction.Debit(8)),
      2 => Some(Transaction.Credit(25)),
      3 => Some(Transaction.Debit(5)),
      _ => None,
    }
    self.index = self.index + 1
    transaction
  }
}

extend(Batch, IntoIterator) {
  let Iter = Batch;

  let into_iter = {
    (move self)
    (): Batch =>
    self
  }
}

let count_batch = {
  (move batch: Batch): i32 =>
  let mut count = 0
  for batch { _ =>
    count = count + 1
  }
  count
}

let apply = { with<overdraft>
  (ledger: Borrow<mut><Ledger>)
  (move transaction: Transaction): () =>
  match(transaction) {
    Credit(amount) => ledger.credit(amount),
    Debit(amount) => do {
      if(amount > ledger.balance) {
        overdraft.reject()
      } else: {
        ledger.debit(amount)
      }
    },
  }
}

let process = { with<overdraft>
  (move batch: Batch): i32 =>
  let mut ledger = Ledger { balance: 0, processed: 0 }
  for batch { transaction =>
    apply(ledger)(transaction)
  }
  ledger.snapshot()
}

let main = {
  (): i32 =>
  let balance = overdraft.handle {
    reject: { () => 1 },
    action: { process(Batch { index: 0 }) },
  }
  let count = count_batch(Batch { index: 0 })
  if(balance == 42 && count == 4) { 42 } else: { 1 }
}
