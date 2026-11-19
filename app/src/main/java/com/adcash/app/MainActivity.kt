@file:OptIn(ExperimentalMaterial3Api::class)

package com.adcash.app

import android.app.Activity
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adcash.app.network.ApiClient
import com.adcash.app.storage.SecureStorage
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import kotlinx.coroutines.launch
import java.util.Locale

data class LedgerItem(
    val title: String,
    val subtitle: String,
    val amountText: String,
    val status: String,
    val isCredit: Boolean
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MobileAds.initialize(this) {}
        val storage = SecureStorage(this)

        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Color(0xFF4F46E5),
                    secondary = Color(0xFFF59E0B),
                    background = Color(0xFFF8FAFC)
                )
            ) {
                AdCashApp(storage)
            }
        }
    }
}

@Composable
fun AdCashApp(storage: SecureStorage) {
    var isLoggedIn by remember { mutableStateOf(storage.authToken != null) }
    var selectedTab by remember { mutableIntStateOf(0) }

    if (!isLoggedIn) {
        LoginScreen(
            onLoginSuccess = { phone ->
                storage.phoneNumber = phone
                storage.authToken = "user_$phone"
                isLoggedIn = true
            }
        )
    } else {
        MainScaffold(
            storage = storage,
            selectedTab = selectedTab,
            onSelectTab = { selectedTab = it },
            onLogout = {
                storage.clearAll()
                isLoggedIn = false
                selectedTab = 0
            }
        )
    }
}

@Composable
fun LoginScreen(onLoginSuccess: (String) -> Unit) {
    var phone by remember { mutableStateOf("") }
    var otpSent by remember { mutableStateOf(false) }
    var otp by remember { mutableStateOf("") }
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.PlayCircleFilled,
            contentDescription = null,
            tint = Color(0xFF4F46E5),
            modifier = Modifier.size(56.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text("AdCash", fontSize = 34.sp, fontWeight = FontWeight.Bold, color = Color(0xFF4F46E5))
        Text("Sirf Video Ads Dekhein aur Real UPI Cash Paayein.", color = Color.Gray, fontSize = 15.sp)
        Spacer(modifier = Modifier.height(32.dp))

        OutlinedTextField(
            value = phone,
            onValueChange = { if (it.length <= 10) phone = it.filter { c -> c.isDigit() } },
            label = { Text("Mobile Number (+91)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            modifier = Modifier.fillMaxWidth()
        )

        if (otpSent) {
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = otp,
                onValueChange = { if (it.length <= 6) otp = it.filter { c -> c.isDigit() } },
                label = { Text("Enter OTP (Any 4+ digits for demo)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = {
                if (!otpSent) {
                    if (phone.length == 10) {
                        otpSent = true
                        Toast.makeText(context, "OTP sent to +91 $phone", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "10-digit mobile number dalein", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    if (otp.length >= 4) {
                        onLoginSuccess(phone)
                    } else {
                        Toast.makeText(context, "Valid OTP dalein", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            Text(if (otpSent) "Verify & Start Watching Ads" else "Send OTP", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun MainScaffold(
    storage: SecureStorage,
    selectedTab: Int,
    onSelectTab: (Int) -> Unit,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()

    var earnedCoins by remember { mutableIntStateOf(storage.earnedCoins) }
    var withdrawnCoins by remember { mutableIntStateOf(storage.withdrawnCoins) }
    var totalAds by remember { mutableIntStateOf(storage.totalAdsWatched) }
    var todayAds by remember { mutableIntStateOf(storage.getTodayAdsCount()) }

    val dailyAdLimit = 20
    val coinsPerAd = 50 // 50 Coins = ₹0.50 per video ad

    var rewardedAd by remember { mutableStateOf<RewardedAd?>(null) }
    var isAdLoading by remember { mutableStateOf(false) }

    fun loadRewardedAd() {
        if (isAdLoading || rewardedAd != null) return
        isAdLoading = true
        val adRequest = AdRequest.Builder().build()
        RewardedAd.load(
            context,
            storage.rewardedAdUnitId,
            adRequest,
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    rewardedAd = ad
                    isAdLoading = false
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    rewardedAd = null
                    isAdLoading = false
                }
            }
        )
    }

    LaunchedEffect(Unit) {
        loadRewardedAd()
    }

    val availableCoins = (earnedCoins - withdrawnCoins).coerceAtLeast(0)
    val rupeeBalance = availableCoins / 100.0 // 100 Coins = ₹1

    val ledger = remember { mutableStateListOf<LedgerItem>() }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { onSelectTab(0) },
                    icon = { Icon(Icons.Default.PlayCircle, contentDescription = "Watch Ads") },
                    label = { Text("Watch Ads") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { onSelectTab(1) },
                    icon = { Icon(Icons.Default.AccountBalanceWallet, contentDescription = "Withdraw") },
                    label = { Text("UPI Cash") }
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { onSelectTab(2) },
                    icon = { Icon(Icons.Default.History, contentDescription = "History") },
                    label = { Text("History") }
                )
                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = { onSelectTab(3) },
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                    label = { Text("Settings") }
                )
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            when (selectedTab) {
                0 -> WatchAdsHomeScreen(
                    coins = availableCoins,
                    rupees = rupeeBalance,
                    todayAds = todayAds,
                    dailyLimit = dailyAdLimit,
                    totalAds = totalAds,
                    isAdReady = rewardedAd != null,
                    isAdLoading = isAdLoading,
                    onWatchAdClick = {
                        if (todayAds >= dailyAdLimit) {
                            Toast.makeText(context, "Aaj ki $dailyAdLimit ads ki limit poori ho gayi hai!", Toast.LENGTH_SHORT).show()
                            return@WatchAdsHomeScreen
                        }
                        val ad = rewardedAd
                        if (ad != null && activity != null) {
                            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                                override fun onAdDismissedFullScreenContent() {
                                    rewardedAd = null
                                    loadRewardedAd()
                                }

                                override fun onAdFailedToShowFullScreenContent(err: AdError) {
                                    rewardedAd = null
                                    loadRewardedAd()
                                }
                            }
                            ad.show(activity) {
                                earnedCoins += coinsPerAd
                                totalAds += 1
                                storage.earnedCoins = earnedCoins
                                storage.totalAdsWatched = totalAds
                                storage.incrementTodayAdsCount()
                                todayAds = storage.getTodayAdsCount()

                                ledger.add(
                                    0,
                                    LedgerItem(
                                        title = "Video Ad Watched (#$totalAds)",
                                        subtitle = "Rewarded Ad Completed",
                                        amountText = "+50 Coins (₹0.50)",
                                        status = "Credited",
                                        isCredit = true
                                    )
                                )
                                Toast.makeText(context, "+50 Coins (₹0.50) Wallet mein jud gaye!", Toast.LENGTH_SHORT).show()
                                scope.launch {
                                    ApiClient.syncAdReward(
                                        baseUrl = storage.serverUrl,
                                        token = storage.authToken ?: "",
                                        phone = storage.phoneNumber
                                    )
                                }
                            }
                        } else {
                            Toast.makeText(context, "Google Video Ad load ho raha hai... 3 second baad dabayein.", Toast.LENGTH_SHORT).show()
                            loadRewardedAd()
                        }
                    },
                    onGoWithdraw = { onSelectTab(1) }
                )
                1 -> UpiWithdrawScreen(
                    rupeeBalance = rupeeBalance,
                    availableCoins = availableCoins,
                    onSubmitUpiRequest = { upiId, rupees, onDone ->
                        scope.launch {
                            val coinsNeeded = (rupees * 100).toInt()
                            val (success, msg) = ApiClient.submitUpiWithdrawalRequest(
                                baseUrl = storage.serverUrl,
                                token = storage.authToken ?: "",
                                phone = storage.phoneNumber,
                                upiId = upiId,
                                rupees = rupees,
                                coins = coinsNeeded
                            )
                            onDone()
                            if (success) {
                                withdrawnCoins += coinsNeeded
                                storage.withdrawnCoins = withdrawnCoins
                                ledger.add(
                                    0,
                                    LedgerItem(
                                        title = "UPI Withdrawal ($upiId)",
                                        subtitle = "Sent to Admin Payout Queue",
                                        amountText = "-₹${String.format(Locale.US, "%.2f", rupees)}",
                                        status = "Pending Payment",
                                        isCredit = false
                                    )
                                )
                            }
                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                        }
                    }
                )
                2 -> HistoryScreen(ledger = ledger)
                3 -> SettingsScreen(
                    storage = storage,
                    onLogout = onLogout
                )
            }
        }
    }
}

@Composable
fun WatchAdsHomeScreen(
    coins: Int,
    rupees: Double,
    todayAds: Int,
    dailyLimit: Int,
    totalAds: Int,
    isAdReady: Boolean,
    isAdLoading: Boolean,
    onWatchAdClick: () -> Unit,
    onGoWithdraw: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("AdCash", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color(0xFF4F46E5))
            Surface(
                color = Color(0xFFEEF2FF),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    text = "Total Ads: $totalAds",
                    color = Color(0xFF4F46E5),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Balance Card
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF4F46E5)),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text("Your Earnings", color = Color.White.copy(alpha = 0.85f))
                Text(
                    text = "₹${String.format(Locale.US, "%.2f", rupees)}",
                    fontSize = 38.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text("$coins Coins (100 Coins = ₹1)", color = Color(0xFFC7D2FE))
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onGoWithdraw,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF4F46E5))
                ) {
                    Text("Withdraw to UPI", fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Main Watch Video Ad Card
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFBEB)),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Default.OndemandVideo,
                    contentDescription = null,
                    tint = Color(0xFFD97706),
                    modifier = Modifier.size(52.dp)
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    "Watch Video Ad & Earn",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF92400E)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "1 Video Ad = +50 Coins (₹0.50)",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFB45309)
                )
                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onWatchAdClick,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = when {
                            todayAds >= dailyAdLimit -> "Daily Limit Reached ($todayAds/$dailyLimit)"
                            isAdReady -> "Watch Video Ad Now (+50 Coins)"
                            isAdLoading -> "Loading Video Ad..."
                            else -> "Load & Watch Ad (+50 Coins)"
                        },
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Daily Limit Progress Card
        Card(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Today's Ad Limit", fontWeight = FontWeight.SemiBold)
                    Text("$todayAds / $dailyLimit Ads", fontWeight = FontWeight.Bold, color = Color(0xFF4F46E5))
                }
                Spacer(modifier = Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { (todayAds.toFloat() / dailyLimit.toFloat()).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "No special permissions needed. Just watch video ads and redeem via UPI.",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }
        }
    }
}

@Composable
fun UpiWithdrawScreen(
    rupeeBalance: Double,
    availableCoins: Int,
    onSubmitUpiRequest: (String, Double, () -> Unit) -> Unit
) {
    var upiId by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Text("Withdraw via UPI", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text("Available: ₹${String.format(Locale.US, "%.2f", rupeeBalance)} ($availableCoins Coins)", color = Color(0xFF4F46E5), fontWeight = FontWeight.SemiBold)
        Text("Minimum Withdrawal: ₹10 (1000 Coins)", fontSize = 13.sp, color = Color.Gray)
        Spacer(modifier = Modifier.height(20.dp))

        OutlinedTextField(
            value = upiId,
            onValueChange = { upiId = it.trim() },
            label = { Text("Enter UPI ID (e.g. mobile@ybl / name@okicici)") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = amountText,
            onValueChange = { amountText = it },
            label = { Text("Amount in ₹ (Min ₹10)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(20.dp))
        Button(
            enabled = !isSubmitting,
            onClick = {
                val amt = amountText.toDoubleOrNull() ?: 0.0
                when {
                    !upiId.contains("@") || upiId.length < 5 ->
                        Toast.makeText(context, "Sahi UPI ID dalein", Toast.LENGTH_SHORT).show()
                    amt < 10.0 ->
                        Toast.makeText(context, "Minimum withdrawal ₹10 hai", Toast.LENGTH_SHORT).show()
                    amt > rupeeBalance ->
                        Toast.makeText(context, "Aapke wallet mein itne coins nahi hain", Toast.LENGTH_SHORT).show()
                    else -> {
                        isSubmitting = true
                        onSubmitUpiRequest(upiId, amt) {
                            isSubmitting = false
                            amountText = ""
                        }
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            Text(if (isSubmitting) "Submitting..." else "Request UPI Cash", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun HistoryScreen(ledger: List<LedgerItem>) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
    ) {
        Text("Ad Rewards & UPI History", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))
        if (ledger.isEmpty()) {
            Text("Abhi tak koi video ad nahi dekha gaya hai.", color = Color.Gray)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(ledger) { item ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.title, fontWeight = FontWeight.Bold)
                                Text("${item.subtitle} • ${item.status}", fontSize = 12.sp, color = Color.Gray)
                            }
                            Text(
                                item.amountText,
                                fontWeight = FontWeight.Bold,
                                color = if (item.isCredit) Color(0xFF059669) else Color(0xFFDC2626)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(
    storage: SecureStorage,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    var serverUrlInput by remember { mutableStateOf(storage.serverUrl) }
    var adUnitInput by remember { mutableStateOf(storage.rewardedAdUnitId) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Text("AdMob & Server Settings", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))

        Text("Google AdMob Rewarded Ad Unit ID", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        Spacer(modifier = Modifier.height(6.dp))
        OutlinedTextField(
            value = adUnitInput,
            onValueChange = { adUnitInput = it },
            label = { Text("Ad Unit ID (Default: Google Test ID)") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))

        Text("Backend Server URL (Render.com)", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        Spacer(modifier = Modifier.height(6.dp))
        OutlinedTextField(
            value = serverUrlInput,
            onValueChange = { serverUrlInput = it },
            label = { Text("Server URL") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(10.dp))
        Button(
            onClick = {
                storage.rewardedAdUnitId = adUnitInput
                storage.serverUrl = serverUrlInput
                Toast.makeText(context, "Settings Saved!", Toast.LENGTH_SHORT).show()
            }
        ) {
            Text("Save Settings")
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 20.dp))

        OutlinedButton(
            onClick = onLogout,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFDC2626)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Logout, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Reset / Logout Account")
        }
    }
}
