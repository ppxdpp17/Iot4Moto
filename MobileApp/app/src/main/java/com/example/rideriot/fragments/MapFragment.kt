package com.example.rideriot.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.rideriot.R // O teu R

class MapFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        // Terás de criar um fragment_map.xml mais tarde
        return inflater.inflate(R.layout.fragment_map, container, false)
    }
}