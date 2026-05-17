package com.example.nammahomestay

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import java.util.UUID

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppNavigation()
                }
            }
        }
    }
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val currentUser = FirebaseAuth.getInstance().currentUser

    // FIX: NavType.BoolType cannot be used with a string literal start destination like
    // "list_homestays/false". Use a neutral start destination and redirect from there.
    val startDestination = if (currentUser != null) "list_homestays_loader" else "welcome"

    NavHost(navController = navController, startDestination = startDestination) {
        composable("welcome") {
            AuthScreen(onLoginSuccess = { isHost ->
                // Navigate with the bool arg properly via the loader route
                navController.navigate(if (isHost) "list_homestays/1" else "list_homestays/0") {
                    popUpTo("welcome") { inclusive = true }
                }
            })
        }

        // FIX: Intermediate loader for when user is already logged in (avoids passing
        // a bool literal as a start destination string which crashes NavHost).
        composable("list_homestays_loader") {
            MyListingsScreen(isHost = false, navController = navController)
        }

        composable("add_homestay") {
            HomeStayForm(onNavigateToList = {
                navController.navigate("list_homestays/1") {
                    popUpTo("add_homestay") { inclusive = true }
                }
            })
        }

        // FIX: NavType.BoolType has known issues with Compose Navigation argument parsing.
        // Use IntType (0 = guest, 1 = host) to avoid the "conflict" / parsing crash.
        composable(
            "list_homestays/{isHost}",
            arguments = listOf(navArgument("isHost") { type = NavType.IntType })
        ) { backStackEntry ->
            val isHost = (backStackEntry.arguments?.getInt("isHost") ?: 0) == 1
            MyListingsScreen(isHost = isHost, navController = navController)
        }

        composable("host_bookings") {
            HostBookingsScreen(onBack = { navController.popBackStack() })
        }
        composable("guest_history") {
            GuestHistoryScreen(navController = navController)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeStayForm(onNavigateToList: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var dailyRate by remember { mutableStateOf("") }
    var hostPhone by remember { mutableStateOf("") }
    var secretSpot by remember { mutableStateOf("") }
    var selectedMenu by remember { mutableStateOf("") }
    var imageUri by remember { mutableStateOf<Uri?>(null) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { imageUri = it }
    val db = FirebaseFirestore.getInstance()
    val storage = FirebaseStorage.getInstance()
    val auth = FirebaseAuth.getInstance()
    val menuOptions = listOf("Akki Rotti", "Bamboo Shoot Curry", "Local Fish Fry", "Veg Thali")

    Column(modifier = Modifier.padding(16.dp).fillMaxSize()) {
        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("HomeStay Name") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = location, onValueChange = { location = it }, label = { Text("Location") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = dailyRate, onValueChange = { dailyRate = it }, label = { Text("Daily Rate (₹)") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = hostPhone, onValueChange = { hostPhone = it }, label = { Text("Host Contact Number") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = secretSpot, onValueChange = { secretSpot = it }, label = { Text("Nearby Secret Spot") }, modifier = Modifier.fillMaxWidth())

        Text("Today's Special:", modifier = Modifier.padding(top = 16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            menuOptions.forEach { item ->
                FilterChip(selected = (selectedMenu == item), onClick = { selectedMenu = item }, label = { Text(item) })
            }
        }
        Button(onClick = { launcher.launch("image/*") }, modifier = Modifier.fillMaxWidth()) { Text("Pick Photo") }
        Button(onClick = {
            val home = hashMapOf(
                "name" to name, "location" to location, "menu" to selectedMenu,
                "dailyRate" to dailyRate, "secretSpot" to secretSpot, "hostPhone" to hostPhone,
                "isFavorite" to false, "ownerId" to (auth.currentUser?.uid ?: "unknown")
            )
            if (imageUri != null) {
                val ref = storage.reference.child("images/${UUID.randomUUID()}")
                ref.putFile(imageUri!!).addOnSuccessListener {
                    ref.downloadUrl.addOnSuccessListener { uri ->
                        home["imageUrl"] = uri.toString()
                        db.collection("homestays").add(home).addOnSuccessListener { onNavigateToList() }
                    }
                }
            } else {
                db.collection("homestays").add(home).addOnSuccessListener { onNavigateToList() }
            }
        }, modifier = Modifier.fillMaxWidth()) { Text("Save HomeStay") }
    }
}

@Composable
fun MyListingsScreen(isHost: Boolean, navController: NavController) {
    var searchQuery by remember { mutableStateOf("") }
    val db = FirebaseFirestore.getInstance()
    val auth = FirebaseAuth.getInstance()
    val context = LocalContext.current
    var homeStays by remember { mutableStateOf<List<Map<String, Any>>?>(null) }

    LaunchedEffect(isHost) {
        val collectionRef = db.collection("homestays")
        val query = if (isHost) {
            collectionRef.whereEqualTo("ownerId", auth.currentUser?.uid ?: "")
        } else {
            collectionRef
        }

        query.get().addOnSuccessListener { querySnapshot ->
            val list = mutableListOf<Map<String, Any>>()
            for (document in querySnapshot.documents) {
                val data = document.data?.toMutableMap() ?: mutableMapOf()
                data["id"] = document.id
                list.add(data)
            }
            homeStays = list
        }.addOnFailureListener {
            Toast.makeText(context, "Error loading listings", Toast.LENGTH_SHORT).show()
        }
    }

    val filtered = homeStays?.filter {
        it["location"]?.toString()?.contains(searchQuery, ignoreCase = true) == true
    } ?: emptyList()

    Column(modifier = Modifier.padding(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Button(onClick = {
                FirebaseAuth.getInstance().signOut()
                navController.navigate("welcome") { popUpTo(0) { inclusive = true } }
            }) { Text("Logout") }

            if (isHost) {
                Button(onClick = { navController.navigate("add_homestay") }) { Text("Add Property") }
                Button(onClick = { navController.navigate("host_bookings") }) { Text("View Bookings") }
            } else {
                Button(onClick = { navController.navigate("guest_history") }) { Text("My Bookings") }
            }
        }

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            label = { Text("Search by Location") },
            modifier = Modifier.fillMaxWidth()
        )

        if (homeStays == null) {
            CircularProgressIndicator()
        } else if (filtered.isEmpty()) {
            Text("No homestays found.", modifier = Modifier.padding(top = 16.dp))
        } else {
            LazyColumn {
                items(filtered) { home ->
                    Card(modifier = Modifier.padding(vertical = 8.dp).fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            home["imageUrl"]?.toString()?.let { url ->
                                AsyncImage(
                                    model = url,
                                    contentDescription = "Homestay Image",
                                    modifier = Modifier.fillMaxWidth().height(150.dp)
                                )
                            }
                            Text("Name: ${home["name"]}", style = MaterialTheme.typography.titleLarge)
                            Text("Location: ${home["location"]}")

                            Row(modifier = Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = {
                                    val phone = home["hostPhone"]?.toString() ?: ""
                                    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))
                                    context.startActivity(intent)
                                }) { Text("Call Host") }

                                if (!isHost) {
                                    Button(onClick = {
                                        val booking = hashMapOf(
                                            "homestayId" to (home["id"] as String),
                                            "guestId" to (auth.currentUser?.uid ?: ""),
                                            "hostId" to (home["ownerId"] as String),
                                            "status" to "Pending"
                                        )
                                        db.collection("bookings").add(booking).addOnSuccessListener {
                                            Toast.makeText(context, "Requested!", Toast.LENGTH_SHORT).show()
                                        }
                                    }) { Text("Request Booking") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GuestHistoryScreen(navController: NavController) {
    val db = FirebaseFirestore.getInstance()
    val auth = FirebaseAuth.getInstance()
    var bookings by remember { mutableStateOf<List<Map<String, Any>>?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        db.collection("bookings")
            .whereEqualTo("guestId", auth.currentUser?.uid)
            .get()
            .addOnSuccessListener { result ->
                val bookingList = mutableListOf<Map<String, Any>>()
                val total = result.size()
                var count = 0

                result.forEach { doc ->
                    val data = doc.data.toMutableMap()
                    val homestayId = data["homestayId"] as String

                    db.collection("homestays").document(homestayId).get().addOnSuccessListener { homeDoc ->
                        data["name"] = homeDoc.getString("name") ?: "Unknown"
                        bookingList.add(data)
                        count++
                        if (count == total) { bookings = bookingList; isLoading = false }
                    }
                }
                if (total == 0) isLoading = false
            }
    }

    Column(modifier = Modifier.padding(16.dp)) {
        Button(onClick = { navController.popBackStack() }) { Text("Back to Listings") }
        Text("My Booking History", style = MaterialTheme.typography.headlineMedium)

        if (isLoading) CircularProgressIndicator()
        else if (bookings.isNullOrEmpty()) Text("No bookings yet.")
        else LazyColumn {
            items(bookings!!) { booking ->
                Card(modifier = Modifier.padding(vertical = 8.dp).fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Place: ${booking["name"]}", style = MaterialTheme.typography.titleMedium)
                        Text("Status: ${booking["status"]}", color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}