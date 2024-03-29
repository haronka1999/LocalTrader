package com.e.kalaka.fragments

import android.content.ContentValues
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.e.kalaka.R
import com.e.kalaka.databinding.FragmentSplashBinding
import com.e.kalaka.models.BusinessOrder
import com.e.kalaka.models.Product
import com.e.kalaka.models.User
import com.e.kalaka.viewModels.PreloadViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*


class SplashFragment : Fragment() {

    //helpers
    private lateinit var binding: FragmentSplashBinding
    private lateinit var userID: String
    private val preloadedData: PreloadViewModel by activityViewModels()

    //firebase
    private var mAuth: FirebaseAuth = FirebaseAuth.getInstance()
    private lateinit var database: FirebaseDatabase
    private lateinit var usersRef: DatabaseReference
    private val mUser = mAuth.currentUser

    //shared preferences
    private lateinit var sharedPreferences: SharedPreferences


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = DataBindingUtil.inflate(inflater, R.layout.fragment_splash, container, false)
        return binding.root
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        //hide bottom navigation
        requireActivity().findViewById<View>(R.id.bottomNavigationView).visibility = View.GONE

        //get shared preferences
        sharedPreferences = requireContext().getSharedPreferences("credentials",Context.MODE_PRIVATE)
        val credentials = sharedPreferences.all

        login(credentials)
    }


    private fun login(preferences: MutableMap<String,*>){

        if (preferences.containsKey("email") && preferences.containsKey("password")){

            val email = preferences["email"] as String
            val password = preferences["password"] as String

            mAuth.signInWithEmailAndPassword(email,password).addOnCompleteListener{task ->
                if (task.isSuccessful){

                    preloadedData.user.observe(viewLifecycleOwner, androidx.lifecycle.Observer {user ->
                        if (user == null) {
                            Log.d("Error", "Couldn't load the user data")
                            Toast.makeText(context,"Sikertelen bejelentkezés",Toast.LENGTH_LONG).show()
                            findNavController().navigate(R.id.action_splashFragment_to_registerFragment)
                        } else {
                            findNavController().navigate(R.id.action_splashFragment_to_homeFragment)
                        }
                    })

                    loadUserData()
                }
                else{
                    //clear shared preferences
                    sharedPreferences.edit().clear().apply()
                    findNavController().navigate(R.id.action_splashFragment_to_registerFragment)
                }
            }
        }
        else{
            findNavController().navigate(R.id.action_splashFragment_to_registerFragment)
        }
    }

    private fun loadUserData() {

        database = FirebaseDatabase.getInstance()
        usersRef = database.getReference("users")

        preloadedData.userEmails.value = mutableListOf()

        userID = mUser?.uid.toString()

        usersRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {

                // get all emails from database (is needed for autocomplete search)
                for (user in dataSnapshot.children) {
                    val newValue = Pair(
                        user.child("userId").value.toString(),
                        user.child("email").value.toString()
                    )
                    preloadedData.userEmails.value!!.add(newValue)
                }

                // retrieve user data from database
                val userData = dataSnapshot.child(userID)
                userData.child("favorites").children.forEach {
                    val productId = it.value.toString()
                    addFavoriteProductToViewModel(productId)
                }

                // create new User instance
                val user = User(
                    userData.child("businessId").value.toString(),
                    userData.child("email").value.toString(),
                    mutableListOf(),
                    userData.child("firstName").value.toString(),
                    userData.child("userId").value.toString(),
                    userData.child("lastName").value.toString(),
                    mutableListOf(),
                    userData.child("photoURL").value.toString()
                )
                // set the User instance in the viewmodel
                preloadedData.user.value = user
                loadPendingOrders(user.userId)
            }

            override fun onCancelled(error: DatabaseError) {
                // Failed to read value
                Log.w(
                    ContentValues.TAG,
                    "Failed to read value.",
                    error.toException()
                )
            }
        })
    }

    private fun loadPendingOrders(userId: String) {
        val myRefBusiness = database.getReference("business")

        myRefBusiness.addValueEventListener(object: ValueEventListener {

            override fun onDataChange(snapshot: DataSnapshot) {
                val newList = mutableListOf<BusinessOrder>()

                // hosszan megyünk a vállalkozásokon
                for(business in snapshot.children) {
                    // hosszan megyünk egy vállalkozás tagjain
                    for(member in business.child("memberIds").children) {
                        // ha a user tagja a vállalkozásnak, akkor a vállalkozás rendelései hozzá tartoznak
                        if(member.value.toString() == userId) {
                            for(orders in business.child("orders").children ) {
                                val order = BusinessOrder(
                                    orders.child("address").value.toString(),
                                    orders.child("city").value.toString(),
                                    orders.child("clientId").value.toString(),
                                    orders.child("comment").value.toString(),
                                    orders.child("number").value.toString(),
                                    orders.child("orderId").value.toString(),
                                    orders.child("postcode").value.toString(),
                                    orders.child("productId").value.toString(),
                                    orders.child("productName").value.toString(),
                                    orders.child("status").value.toString().toInt(),
                                    orders.child("time").value.toString(),
                                    orders.child("total").value.toString().toDouble(),
                                    orders.child("worker").value.toString()
                                )
                                newList.add(order)
                            }
                        }
                    }
                }

                preloadedData.pendingOrders.value = newList
            }

            override fun onCancelled(error: DatabaseError) {}
        })
    }


    private fun addFavoriteProductToViewModel(productId: String) {
        database = FirebaseDatabase.getInstance()
        val productsRef = database.getReference("products")

        productsRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val prod = snapshot.child(productId)
                val product = Product(
                    prod.child("businessId").value.toString(),
                    prod.child("description").value.toString(),
                    prod.child("name").value.toString(),
                    prod.child("photoURL").value.toString(),
                    prod.child("price").value.toString().toDouble(),
                    prod.child("productId").value.toString()
                )

                if (preloadedData.favoriteProductlist.value == null) {
                    preloadedData.favoriteProductlist.value = mutableListOf(product)
                } else {
                    preloadedData.favoriteProductlist.value!!.add(product)
                }
            }

            override fun onCancelled(error: DatabaseError) {}
        })
    }

    override fun onStop() {
        super.onStop()

    }
}