require('dotenv').config();
const express = require('express');
const cors = require('cors');
const axios = require('axios');
const Database = require('better-sqlite3');
const cron = require('node-cron');

const app = express();
app.use(cors());
app.use(express.json());

const db = new Database('thennaval.db');

// 1. Initialize SQLite Tables
db.exec(`
  CREATE TABLE IF NOT EXISTS products (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL,
    sku TEXT UNIQUE,
    price REAL NOT NULL,
    stock INTEGER NOT NULL DEFAULT 0,
    low_stock_threshold INTEGER NOT NULL DEFAULT 5
  );

  CREATE TABLE IF NOT EXISTS orders (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    customer_name TEXT NOT NULL,
    customer_phone TEXT NOT NULL,
    items_json TEXT NOT NULL,
    total_amount REAL NOT NULL,
    payment_status TEXT NOT NULL DEFAULT 'PENDING',
    payment_method TEXT DEFAULT 'UPI',
    order_status TEXT DEFAULT 'CONFIRMED',
    created_at TEXT DEFAULT (datetime('now', 'localtime'))
  );

  CREATE TABLE IF NOT EXISTS payments (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    order_id INTEGER NOT NULL,
    amount REAL NOT NULL,
    method TEXT NOT NULL,
    paid_at TEXT DEFAULT (datetime('now', 'localtime')),
    FOREIGN KEY(order_id) REFERENCES orders(id)
  );
`);

// Seed initial sample inventory if empty
const count = db.prepare('SELECT COUNT(*) as c FROM products').get().c;
if (count === 0) {
  const insert = db.prepare('INSERT INTO products (name, sku, price, stock, low_stock_threshold) VALUES (?, ?, ?, ?, ?)');
  insert.run('Cold Pressed Groundnut Oil (1L)', 'OIL-GN-1L', 240, 18, 5);
  insert.run('Cold Pressed Gingelly Oil (1L)', 'OIL-GG-1L', 360, 4, 5);
  insert.run('Palm Jaggery (Karupatti 1kg)', 'JAG-KM-1K', 280, 0, 5);
}

// 2. WhatsApp Cloud API Helper
async function sendWhatsAppMessage(toPhone, textBody) {
  if (!process.env.WA_TOKEN || !process.env.WA_PHONE_NUMBER_ID) {
    console.log(`[SIMULATED WA -> ${toPhone}]:\n${textBody}\n`);
    return { simulated: true };
  }
  try {
    const cleanPhone = toPhone.replace(/\D/g, '');
    const url = `https://graph.facebook.com/v24.0/${process.env.WA_PHONE_NUMBER_ID}/messages`;
    const res = await axios.post(
      url,
      {
        messaging_product: 'whatsapp',
        recipient_type: 'individual',
        to: cleanPhone.startsWith('91') ? cleanPhone : `91${cleanPhone}`,
        type: 'text',
        text: { body: textBody }
      },
      { headers: { Authorization: `Bearer ${process.env.WA_TOKEN}` } }
    );
    return res.data;
  } catch (err) {
    console.error('WhatsApp API Error:', err.response?.data || err.message);
    return { error: true };
  }
}

function notifyOwner(message) {
  return sendWhatsAppMessage(process.env.OWNER_PHONE, `🔔 *THENNAVAL AI ALERT*\n${message}`);
}

function evaluateStockAlerts(product) {
  if (product.stock <= 0) {
    notifyOwner(`❌ *OUT OF STOCK ALERT*\nItem: ${product.name} (${product.sku})\nCurrent Stock: 0\nAction: Restock immediately.`);
  } else if (product.stock <= product.low_stock_threshold) {
    notifyOwner(`⚠️ *LOW STOCK ALERT*\nItem: ${product.name} (${product.sku})\nRemaining: ${product.stock} units (Threshold: ${product.low_stock_threshold})`);
  }
}

// 3. Inventory Routes
app.get('/api/products', (req, res) => {
  const products = db.prepare('SELECT * FROM products ORDER BY stock ASC').all();
  const formatted = products.map(p => ({
    ...p,
    status: p.stock <= 0 ? 'OUT_OF_STOCK' : p.stock <= p.low_stock_threshold ? 'LOW_STOCK' : 'IN_STOCK'
  }));
  res.json(formatted);
});

app.post('/api/products', (req, res) => {
  const { name, sku, price, stock, low_stock_threshold } = req.body;
  const info = db.prepare(
    'INSERT INTO products (name, sku, price, stock, low_stock_threshold) VALUES (?, ?, ?, ?, ?)'
  ).run(name, sku, Number(price), Number(stock), Number(low_stock_threshold || 5));
  const product = db.prepare('SELECT * FROM products WHERE id = ?').get(info.lastInsertRowid);
  evaluateStockAlerts(product);
  res.json(product);
});

app.patch('/api/products/:id/stock', (req, res) => {
  const { delta } = req.body;
  db.prepare('UPDATE products SET stock = MAX(0, stock + ?) WHERE id = ?').run(Number(delta), req.params.id);
  const product = db.prepare('SELECT * FROM products WHERE id = ?').get(req.params.id);
  evaluateStockAlerts(product);
  res.json(product);
});

// 4. Orders & Payments Routes
app.get('/api/orders', (req, res) => {
  const orders = db.prepare('SELECT * FROM orders ORDER BY id DESC').all();
  res.json(orders.map(o => ({ ...o, items: JSON.parse(o.items_json) })));
});

app.post('/api/orders', async (req, res) => {
  const { customer_name, customer_phone, items, payment_status, payment_method, send_auto_wa } = req.body;
  let total_amount = 0;
  for (const item of items) {
    total_amount += item.qty * item.price;
  }

  const createOrderTx = db.transaction(() => {
    const orderInfo = db.prepare(
      'INSERT INTO orders (customer_name, customer_phone, items_json, total_amount, payment_status, payment_method) VALUES (?, ?, ?, ?, ?, ?)'
    ).run(customer_name, customer_phone, JSON.stringify(items), total_amount, payment_status, payment_method);

    const orderId = orderInfo.lastInsertRowid;

    if (payment_status === 'PAID') {
      db.prepare('INSERT INTO payments (order_id, amount, method) VALUES (?, ?, ?)').run(orderId, total_amount, payment_method);
    }

    for (const item of items) {
      db.prepare('UPDATE products SET stock = MAX(0, stock - ?) WHERE id = ?').run(item.qty, item.product_id);
      const updatedProd = db.prepare('SELECT * FROM products WHERE id = ?').get(item.product_id);
      if (updatedProd) evaluateStockAlerts(updatedProd);
    }

    return orderId;
  });

  const orderId = createOrderTx();
  const itemSummary = items.map(i => `• ${i.name} x${i.qty} = ₹${i.qty * i.price}`).join('\n');

  notifyOwner(
    `🛒 *NEW ORDER #${orderId}*\nCustomer: ${customer_name} (${customer_phone})\n${itemSummary}\nTotal: ₹${total_amount}\nPayment: ${payment_status} (${payment_method})`
  );

  if (send_auto_wa) {
    await sendWhatsAppMessage(
      customer_phone,
      `🙏 Vanakkam ${customer_name}!\nThank you for ordering from *Thennaval*.\n\n*Order #${orderId}*\n${itemSummary}\n*Total:* ₹${total_amount}\n*Payment Status:* ${payment_status}`
    );
  }

  res.json({ id: orderId, total_amount, status: 'CREATED' });
});

app.post('/api/orders/:id/pay', async (req, res) => {
  const { amount, method, send_auto_wa } = req.body;
  const order = db.prepare('SELECT * FROM orders WHERE id = ?').get(req.params.id);
  if (!order) return res.status(404).json({ error: 'Order not found' });

  db.prepare('INSERT INTO payments (order_id, amount, method) VALUES (?, ?, ?)').run(order.id, Number(amount), method);
  db.prepare("UPDATE orders SET payment_status = 'PAID', payment_method = ? WHERE id = ?").run(method, order.id);

  notifyOwner(`💰 *PAYMENT RECEIVED*\nOrder #${order.id} (${order.customer_name})\nAmount: ₹${amount} via ${method}`);

  if (send_auto_wa) {
    await sendWhatsAppMessage(
      order.customer_phone,
      `✅ *Payment Receipt - Thennaval*\nHi ${order.customer_name}, we received ₹${amount} via ${method} for Order #${order.id}. Thank you!`
    );
  }

  res.json({ success: true });
});

// 5. Daily Sales Records
app.get('/api/sales/daily', (req, res) => {
  const records = db.prepare(`
    SELECT 
      date(created_at) as sale_date,
      COUNT(id) as total_orders,
      SUM(total_amount) as gross_sales,
      SUM(CASE WHEN payment_status = 'PAID' THEN total_amount ELSE 0 END) as collected_amount,
      SUM(CASE WHEN payment_status != 'PAID' THEN total_amount ELSE 0 END) as pending_amount
    FROM orders
    GROUP BY date(created_at)
    ORDER BY sale_date DESC
    LIMIT 30
  `).all();
  res.json(records);
});

cron.schedule('0 21 * * *', () => {
  const today = db.prepare(`
    SELECT 
      COUNT(id) as orders,
      COALESCE(SUM(total_amount), 0) as sales,
      COALESCE(SUM(CASE WHEN payment_status = 'PAID' THEN total_amount ELSE 0 END), 0) as collected
    FROM orders WHERE date(created_at) = date('now', 'localtime')
  `).get();

  const lowCount = db.prepare('SELECT COUNT(*) as c FROM products WHERE stock > 0 AND stock <= low_stock_threshold').get().c;
  const outCount = db.prepare('SELECT COUNT(*) as c FROM products WHERE stock <= 0').get().c;

  notifyOwner(
    `📊 *DAILY SALES REPORT*\nOrders Today: ${today.orders}\nTotal Sales: ₹${today.sales}\nCollected: ₹${today.collected}\nPending: ₹${today.sales - today.collected}\n⚠️ Low Stock Items: ${lowCount}\n❌ Out of Stock Items: ${outCount}`
  );
}, { timezone: 'Asia/Kolkata' });

// 6. Meta WhatsApp Webhook
app.get('/webhook', (req, res) => {
  if (req.query['hub.verify_token'] === process.env.WA_VERIFY_TOKEN) {
    return res.send(req.query['hub.challenge']);
  }
  res.sendStatus(403);
});

app.post('/webhook', async (req, res) => {
  res.sendStatus(200);
  const msg = req.body.entry?.[0]?.changes?.[0]?.value?.messages?.[0];
  if (!msg || msg.type !== 'text') return;

  const from = msg.from;
  const text = msg.text.body.trim().toLowerCase();

  if (text.includes('stock') || text.includes('price') || text.includes('menu') || text === 'hi') {
    const items = db.prepare('SELECT name, price, stock FROM products WHERE stock > 0').all();
    const list = items.length
      ? items.map(i => `• *${i.name}* — ₹${i.price} (${i.stock} available)`).join('\n')
      : 'Currently restocking all items. Please check back shortly!';
    await sendWhatsAppMessage(from, `🌿 *Thennaval Live Catalog*\n\n${list}\n\nReply with the item name & quantity to place an order.`);
  } else {
    notifyOwner(`💬 *CUSTOMER INQUIRY*\nFrom: +${from}\nMessage: "${msg.text.body}"`);
  }
});

app.listen(process.env.PORT || 3000, () => {
  console.log(`Thennaval AI Server running on port ${process.env.PORT || 3000}`);
});
