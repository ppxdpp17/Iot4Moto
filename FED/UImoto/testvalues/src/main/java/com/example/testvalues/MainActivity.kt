package com.example.testvalues


class GrowingValuesGenerator(
    private var MaxValue:Int,
    private var MaxOscilation:Int,
    private var MinOscilation:Int,
)  {
    constructor(MaxValue: Int):this(MaxValue,1,1);
    constructor(MaxValue: Int,Oscilation:Int):this(MaxValue,Oscilation,Oscilation);
    private var CurrentValue = 0;
    private var MinValue = 0;
    private var sign = 1;

    public fun GenerateValue():Int
    {
        CurrentValue += (MinOscilation..MaxOscilation).random() * sign;
        if(CurrentValue >= MaxValue)
        {
            CurrentValue = MaxValue;
            sign = -1;
        }
        else if (CurrentValue <= MinValue)
        {
            CurrentValue = MinValue;
            sign = 1;
        }
        return CurrentValue
    }
}