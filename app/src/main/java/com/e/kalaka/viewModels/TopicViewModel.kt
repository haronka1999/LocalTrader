package com.e.kalaka.viewModels

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.e.kalaka.models.Business
import com.e.kalaka.utils.Tag

class TopicViewModel : ViewModel() {
    var choosedTask : MutableLiveData<Int> = MutableLiveData<Int>()
    var list : MutableLiveData<MutableList<Business>> = MutableLiveData()
    var filteredBusinesslist: MutableLiveData<MutableList<Business>> = MutableLiveData()

    var filteredTagList : MutableLiveData<List<Pair<String, String>>> = MutableLiveData()
}