package com.e.kalaka.fragments

import android.graphics.Rect
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.activity.addCallback
import androidx.core.widget.addTextChangedListener
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.observe
import androidx.navigation.Navigation
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.e.kalaka.R
import com.e.kalaka.adapters.TagListAdapter
import com.e.kalaka.databinding.FragmentHomeBinding
import com.e.kalaka.models.Business
import com.e.kalaka.models.BusinessOrder
import com.e.kalaka.utils.Tag
import com.e.kalaka.viewModels.PreloadViewModel
import com.e.kalaka.viewModels.TopicViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import java.util.*
import kotlin.concurrent.timerTask

class HomeFragment : Fragment(), TagListAdapter.OnItemClickListener {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requireActivity().findViewById<View>(R.id.bottomNavigationView).visibility = View.VISIBLE
    }
    private lateinit var binding: FragmentHomeBinding
    private lateinit var recyclerView: RecyclerView
    private val topicViewModel : TopicViewModel by activityViewModels()
    private val preloadViewModel : PreloadViewModel by activityViewModels()
    private lateinit var database : FirebaseDatabase
    private lateinit var businessId : String
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        requireActivity().findViewById<View>(R.id.bottomNavigationView).visibility = View.VISIBLE

        // Inflate the layout for this fragment
        binding = DataBindingUtil.inflate(inflater, R.layout.fragment_home, container, false)
        topicViewModel.filteredTagList.value = Tag.getTags()
        businessId = preloadViewModel.user.value!!.businessId

        if ( businessId == "0"){
            binding.pendingOrdersButton.visibility=View.GONE
        }else{
            binding.pendingOrdersButton.visibility=View.VISIBLE
        }

        // refresh topics based on search input
        binding.topicSearchBar.addTextChangedListener{
            topicViewModel.filteredTagList.value = Tag.getTags().filter { pair ->
                pair.second.contains(it.toString())
            }
        }

        // initialize recyclerview
        recyclerView = binding.tagRecycler
        recyclerView.setHasFixedSize(true)
        recyclerView.layoutManager = GridLayoutManager(binding.root.context, 3)
        recyclerView.addItemDecoration(SpaceGrid(3, topicViewModel.filteredTagList.value!!.size, true))
        val adapter = TagListAdapter(listOf(), this, binding.root.context)
        recyclerView.adapter = adapter

        topicViewModel.filteredTagList.observe(viewLifecycleOwner) {
            adapter.setData(it)
        }

        binding.pendingOrdersButton.setOnClickListener{
            Navigation.findNavController(requireView()).navigate(R.id.action_homeFragment_to_pendingOrderFragment)
        }

        return binding.root
    }

    override fun onItemClick(position: Int) {

        val selectedTopic = Tag.getTags()[position].second
        startLoadingData(selectedTopic)
        findNavController().navigate(R.id.action_homeFragment_to_mainSearch)
    }

    // class for creating grid in recyclerview
    private inner class SpaceGrid(private val mSpanCount: Int, private val mSpacing: Int, private val mIncludeEdge: Boolean) : RecyclerView.ItemDecoration() {

        override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
            val position = parent.getChildAdapterPosition(view)
            val column = position % mSpanCount

            if (mIncludeEdge) {
                outRect.left = mSpacing - column * mSpacing / mSpanCount
                outRect.right = (column + 1) * mSpacing / mSpanCount
                if (position < mSpanCount) {
                    outRect.top = mSpacing
                }
                outRect.bottom = mSpacing
            } else {
                outRect.left = column * mSpacing / mSpanCount
                outRect.right = mSpacing - (column + 1) * mSpacing / mSpanCount
                if (position < mSpanCount) {
                    outRect.top = mSpacing
                }
            }
        }
    }

    private fun startLoadingData(selectedTopic : String){
        database = FirebaseDatabase.getInstance()
        topicViewModel.list.value = mutableListOf()
        val reference = database.getReference("business")
        reference.addListenerForSingleValueEvent(object : ValueEventListener{
            override fun onDataChange(snapshot: DataSnapshot) {

                //val list = mutableListOf<Business>()

                for (business in snapshot.children ){

                    for (tag in business.child("tags").children){

                        if (tag.value.toString() == selectedTopic){
                            val tags = mutableListOf<String>()
                            val memberIds = mutableListOf<String>()
                            val productIds = mutableListOf<String>()
                            val orders = mutableListOf<BusinessOrder>()


                            for (tag in business.child("tags").children){
                                tags.add(tag.value.toString())
                            }

                            for (memberId in business.child("memberIds").children){
                                memberIds.add(memberId.value.toString())
                            }
                            for (productId in business.child("productIds").children){
                                productIds.add(productId.value.toString())
                            }
                            for (order in business.child("orders").children){
                                val ord = BusinessOrder(
                                    order.child("address").value.toString(),
                                    order.child("city").value.toString(),
                                    order.child("clientId").value.toString(),
                                    order.child("comment").value.toString(),
                                    order.child("number").value.toString(),
                                    order.child("orderId").value.toString(),
                                    order.child("postcode").value.toString(),
                                    order.child("productId").value.toString(),
                                    order.child("productName").value.toString(),
                                    order.child("status").value.toString().toInt(),
                                    order.child("time").value.toString(),
                                    order.child("total").value.toString().toDouble(),
                                    order.child("worker").value.toString()
                                )
                                orders.add(ord)
                            }

                            val item = Business(
                                business.child("businessId").value.toString(),
                                business.child("description").value.toString(),
                                business.child("email").value.toString(),
                                business.child("facebookURL").value.toString(),
                                business.child("instagramURL").value.toString(),
                                business.child("location").value.toString(),
                                business.child("logoURL").value.toString(),
                                memberIds,
                                business.child("name").value.toString(),
                                orders,
                                business.child("ownerId").value.toString(),
                                business.child("phone").value.toString(),
                                productIds,
                                tags
                            )
                            ///list.add(item)
                            topicViewModel.list.value!!.add(item)
                            Log.d("Helo",  "Order: $item")
                        }
                    }
                }
                //topicViewModel.list.value = list
            }

            override fun onCancelled(error: DatabaseError) {}
        })

    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        var callbackCounter = 0
        requireActivity().onBackPressedDispatcher.addCallback(this) {
            if (callbackCounter == 0) {
                Toast.makeText(requireContext(), "Nyomja meg újra a kilépéshez!", Toast.LENGTH_SHORT).show()
                Timer().schedule(timerTask {
                    callbackCounter = 0
                }, 2000)

                callbackCounter++
            } else requireActivity().finish()
        }

    }
}