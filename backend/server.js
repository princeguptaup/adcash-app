const express = require('express');
const crypto = require('crypto');
const app = express();
app.use(express.json());

const ADMIN_PASSWORD = process.env.ADMIN_PASSWORD || 'admin123';

const users = new Map();
const upiRequests = [];

function getOrCreateUser(userId, phone) {
  if (!users.has(userId)) {
    users.set(userId, { userId, phone: phone || 'Unknown', coins: 0, adsWatched: 0 });
  }
  return users.get(userId);
}

app.get('/', (req, res) => {
  res.json({ status: 'AdCash Watch-Ads & UPI Backend Running' });
});

// 1. AdMob Rewarded Video Sync (+50 Coins = ₹0.50 per ad)
app.post('/ads/reward', (req, res) => {
  const userId = (req.headers.authorization || '').replace('Bearer ', '');
  const { phone } = req.body;
  if (!userId) return res.status(401).json({ error: 'Unauthorized' });

  const user = getOrCreateUser(userId, phone);
  user.coins += 50;
  user.adsWatched += 1;

  res.json({ success: true, addedCoins: 50, totalCoins: user.coins, adsWatched: user.adsWatched });
});

// 2. Submit UPI Withdrawal Request
app.post('/withdrawals/upi-request', (req, res) => {
  const userId = (req.headers.authorization || '').replace('Bearer ', '');
  const { phone, upiId, rupees, coins } = req.body;

  if (!upiId || !upiId.includes('@') || rupees < 10) {
    return res.status(400).json({ error: 'Valid UPI ID and minimum ₹10 required' });
  }

  const requestObj = {
    requestId: 'UPI-' + crypto.randomBytes(3).toString('hex').toUpperCase(),
    userId,
    phone: phone || 'Unknown',
    upiId,
    rupees,
    coins,
    status: 'PENDING',
    createdAt: new Date().toISOString()
  };
  upiRequests.unshift(requestObj);

  res.json({ success: true, requestId: requestObj.requestId, status: 'PENDING' });
});

// 3. Admin View All Pending UPI Payouts
app.get('/admin/payouts', (req, res) => {
  if (req.query.key !== ADMIN_PASSWORD) {
    return res.status(401).json({ error: 'Invalid admin key. Use ?key=admin123' });
  }
  res.json({ totalRequests: upiRequests.length, requests: upiRequests });
});

// 4. Admin Mark Payout as PAID
app.post('/admin/payouts/:requestId/mark-paid', (req, res) => {
  if (req.query.key !== ADMIN_PASSWORD) {
    return res.status(401).json({ error: 'Invalid admin key' });
  }
  const item = upiRequests.find(r => r.requestId === req.params.requestId);
  if (!item) return res.status(404).json({ error: 'Request not found' });
  item.status = 'PAID';
  item.paidAt = new Date().toISOString();
  res.json({ success: true, item });
});

const PORT = process.env.PORT || 3000;
app.listen(PORT, () => console.log(`AdCash Server running on port ${PORT}`));
